package codeit.ootd.domain.auth.integration;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.entity.RefreshToken;
import codeit.ootd.domain.auth.exception.InvalidRefreshTokenException;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.auth.repository.RefreshTokenRepository;
import codeit.ootd.domain.auth.service.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class RefreshTokenConcurrencyIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ootd_test")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void postgresContainer_startsSuccessfully() {
        assertTrue(postgres.isRunning());
    }

    @Test
    @DisplayName("PostgreSQL에서 세션 생성 후 Refresh Token을 발급할 수 있다")
    void issueRefreshToken_withPersistedSession_success() {
        // given
        UUID userId = UUID.randomUUID();

        AuthSession session = AuthSession.create(
                userId,
                Instant.now().plus(Duration.ofDays(30))
        );

        AuthSession savedSession = authSessionRepository.saveAndFlush(session);

        // when
        String rawToken = refreshTokenService.issue(savedSession.getId());

        // then
        assertAll(
                () -> assertNotNull(savedSession.getId()),
                () -> assertNotNull(rawToken),
                () -> assertFalse(rawToken.isBlank())
        );
    }

    @Test
    @DisplayName("동일한 Refresh Token으로 동시에 재발급하면 하나만 성공한다")
    void rotate_concurrentRequests_onlyOneSucceeds() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        AuthSession session = authSessionRepository.saveAndFlush(
                AuthSession.create(
                        userId,
                        Instant.now().plus(Duration.ofDays(30))
                )
        );

        UUID sessionId = session.getId();
        String oldRawToken = refreshTokenService.issue(sessionId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<String> task = () -> {
            ready.countDown();

            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("시작 신호 대기 시간 초과");
            }

            return refreshTokenService.rotate(sessionId, oldRawToken);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(task);
            Future<String> second = executor.submit(task);

            assertTrue(ready.await(5, TimeUnit.SECONDS));

            start.countDown();

            int successCount = 0;
            int failureCount = 0;
            String successfulToken = null;

            for (Future<String> future : List.of(first, second)) {
                try {
                    successfulToken = future.get(10, TimeUnit.SECONDS);
                    successCount++;
                }
                catch (ExecutionException e) {
                    if (e.getCause() instanceof InvalidRefreshTokenException) {
                        failureCount++;
                    } else {
                        throw e;
                    }
                }
            }

            // then
            assertEquals(1, successCount);
            assertEquals(1, failureCount);
            assertNotNull(successfulToken);

            RefreshToken savedToken = refreshTokenRepository
                    .findBySessionId(sessionId)
                    .orElseThrow();

            assertEquals(sha256(successfulToken), savedToken.getTokenHash());
            assertNotEquals(sha256(oldRawToken), savedToken.getTokenHash());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("첫 번째 트랜잭션이 행 잠금을 유지하면 두 번째 트랜잭션은 대기한다")
    void findBySessionIdForUpdate_blocksConcurrentTransaction() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        AuthSession session = authSessionRepository.saveAndFlush(
                AuthSession.create(
                        userId,
                        Instant.now().plus(Duration.ofDays(30))
                )
        );

        UUID sessionId = session.getId();
        refreshTokenService.issue(sessionId);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // 트랜잭션 A: 행 잠금을 획득하고 유지
            Future<?> first = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        refreshTokenRepository.findBySessionIdForUpdate(sessionId)
                                .orElseThrow();

                        lockAcquired.countDown();

                        try {
                            if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("잠금 해제 대기 시간 초과");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    })
            );

            assertTrue(lockAcquired.await(5, TimeUnit.SECONDS));

            // 트랜잭션 B: 같은 행에 잠금 요청
            Future<?> second = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        secondStarted.countDown();

                        refreshTokenRepository.findBySessionIdForUpdate(sessionId)
                                .orElseThrow();
                    })
            );

            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));

            // A가 잠금을 유지하는 동안 B는 완료되지 않아야 한다.
            assertThrows(
                    TimeoutException.class,
                    () -> second.get(300, TimeUnit.MILLISECONDS)
            );

            // A의 트랜잭션이 끝나도록 잠금 해제
            releaseLock.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    private String sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}