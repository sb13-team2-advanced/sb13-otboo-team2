package codeit.ootd.domain.auth.integration;

import codeit.ootd.domain.auth.exception.PasswordResetRateLimitExceededException;
import codeit.ootd.domain.auth.service.impl.RedisPasswordResetRateLimiter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisPasswordResetRateLimiterIntegrationTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            ).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisPasswordResetRateLimiter rateLimiter;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(),
                redis.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        rateLimiter =
                new RedisPasswordResetRateLimiter(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("동일 이메일로 재요청하면 IP가 달라도 차단한다")
    void check_sameEmail_rateLimitExceeded() {
        // given
        String email = UUID.randomUUID() + "@example.com";

        // when
        rateLimiter.check(email, "192.0.2.1");

        // then
        assertThatThrownBy(() ->
                rateLimiter.check(email, "192.0.2.2")
        ).isInstanceOf(
                PasswordResetRateLimitExceededException.class
        );
    }

    @Test
    @DisplayName("동일 IP에서 6번째 요청은 차단한다")
    void check_sameIp_rateLimitExceeded() {
        // given
        String ipAddress = "198.51.100.10";

        // when
        for (int i = 0; i < 5; i++) {
            String email = UUID.randomUUID() + "@example.com";

            assertThatCode(() ->
                    rateLimiter.check(email, ipAddress)
            ).doesNotThrowAnyException();
        }

        // then
        assertThatThrownBy(() ->
                rateLimiter.check(
                        UUID.randomUUID() + "@example.com",
                        ipAddress
                )
        ).isInstanceOf(
                PasswordResetRateLimitExceededException.class
        );
    }

    @Test
    @DisplayName("요청 제한 키에 이메일 3분, IP 10분 만료시간을 설정한다")
    void check_setsExpiration() {
        // given
        String email = UUID.randomUUID() + "@example.com";
        String ipAddress = UUID.randomUUID().toString();

        // when
        rateLimiter.check(email, ipAddress);

        // then
        String emailKey = "password-reset:{limit}:email:" + sha256(email);
        String ipKey = "password-reset:{limit}:ip:" + sha256(ipAddress);

        Long emailTtl = redisTemplate.getExpire(
                emailKey, TimeUnit.SECONDS
        );

        Long ipTtl = redisTemplate.getExpire(
                ipKey, TimeUnit.SECONDS
        );

        assertThat(emailTtl).isBetween(1L, 180L);
        assertThat(ipTtl).isBetween(1L, 600L);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] bytes = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(bytes);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }



}
