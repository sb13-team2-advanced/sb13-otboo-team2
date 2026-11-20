package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisClothesExtractionRateLimiterIntegrationTest {

    private static final int LIMIT = 10;

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisClothesExtractionRateLimiter rateLimiter;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        rateLimiter = new RedisClothesExtractionRateLimiter(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void 한도까지는_허용하고_다음_요청부터_차단한다() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < LIMIT; i++) {
            assertThatCode(() -> rateLimiter.check(userId)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimiter.check(userId))
                .isInstanceOfSatisfying(OtbooException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_RATE_LIMIT_EXCEEDED));
    }

    @Test
    void 사용자마다_횟수를_따로_센다() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        for (int i = 0; i < LIMIT; i++) {
            rateLimiter.check(first);
        }

        assertThatCode(() -> rateLimiter.check(second)).doesNotThrowAnyException();
    }

    @Test
    void 요청_횟수_키에_60초_만료시간을_설정한다() {
        UUID userId = UUID.randomUUID();

        rateLimiter.check(userId);

        Long ttl = redisTemplate.getExpire("clothes-extraction:rate:user:" + userId, TimeUnit.SECONDS);

        assertThat(ttl).isBetween(1L, 60L);
    }
}
