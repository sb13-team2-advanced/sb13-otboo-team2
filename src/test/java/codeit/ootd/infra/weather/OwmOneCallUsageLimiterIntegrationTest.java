package codeit.ootd.infra.weather;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class OwmOneCallUsageLimiterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ootd_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private OwmOneCallUsageLimiter limiter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM weather_one_call_usage");
    }

    @Test
    @DisplayName("오늘 첫 One Call 요청은 호출 횟수 1회로 기록한다")
    void reserveFirstRequest() {
        boolean reserved = limiter.tryReserveRequest();

        assertThat(reserved).isTrue();
        assertThat(getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("950번째 요청까지만 허용하고 이후 요청은 거부한다")
    void rejectRequestsAfterDailyLimit() {
        setRequestCount(949);

        boolean lastAllowed = limiter.tryReserveRequest();
        boolean exceeded = limiter.tryReserveRequest();

        assertThat(lastAllowed).isTrue();
        assertThat(exceeded).isFalse();
        assertThat(getRequestCount()).isEqualTo(950);
    }

    @Test
    @DisplayName("동시 요청이 발생해도 하루 호출 제한을 초과하지 않는다")
    void concurrentRequestsDoNotExceedDailyLimit() throws Exception {
        setRequestCount(949);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("첫 번째 요청 대기 시간 초과");
                }
                return limiter.tryReserveRequest();
            });

            Future<Boolean> second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("두 번째 요청 대기 시간 초과");
                }
                return limiter.tryReserveRequest();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            boolean firstResult = first.get(10, TimeUnit.SECONDS);
            boolean secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstResult, secondResult))
                .containsExactlyInAnyOrder(true, false);

            assertThat(getRequestCount()).isEqualTo(950);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("외부 트랜잭션이 롤백되어도 예약한 호출 횟수는 유지된다")
    void reservationRemainsAfterOuterTransactionRollback() {
        TransactionTemplate transactionTemplate =
            new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(limiter.tryReserveRequest()).isTrue();
            status.setRollbackOnly();
        });

        assertThat(getRequestCount()).isEqualTo(1);
    }

    private void setRequestCount(int count) {
        jdbcTemplate.update("""
            INSERT INTO weather_one_call_usage (usage_date, request_count)
            VALUES ((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date, ?)
            """, count);
    }

    private int getRequestCount() {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT request_count
            FROM weather_one_call_usage
            WHERE usage_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date
            """, Integer.class);

        return count;
    }
}
