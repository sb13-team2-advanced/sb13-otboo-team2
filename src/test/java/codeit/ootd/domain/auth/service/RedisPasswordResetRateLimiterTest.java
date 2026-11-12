package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.exception.PasswordResetRateLimitExceededException;
import codeit.ootd.domain.auth.service.impl.RedisPasswordResetRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RedisPasswordResetRateLimiterTest {

    private final StringRedisTemplate redisTemplate =
            mock(StringRedisTemplate.class);

    private final RedisPasswordResetRateLimiter rateLimiter =
            new RedisPasswordResetRateLimiter(redisTemplate);

    @Test
    @DisplayName("첫 번째 비밀번호 초기화 요청은 허용한다")
    void check_firstRequest_allowed() {
        // given
        String email = "test@example.com";
        String ipAddress = "127.0.0.1";

        // when & then
        assertThatCode(() -> rateLimiter.check(email, ipAddress))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("동일 이메일로 3분 이내 재요청하면 차단한다")
    void check_sameEmail_rateLimitExceeded() {
        // given
        String email = "test@example.com";

        // when
        rateLimiter.check(email, "127.0.0.1");

        // then
        assertThatThrownBy(() -> rateLimiter.check(email, "127.0.0.2"))
                .isInstanceOf(PasswordResetRateLimitExceededException.class);
    }

    @Test
    @DisplayName("동일 IP에서 10분 이내 6번째 요청은 차단한다")
    void check_sameIp_rateLimitExceeded() {
        // given
        String ipAddress = "127.0.0.1";

        // when
        for (int i = 0; i < 5; i++) {
            rateLimiter.check("user" + i + "@example.com", ipAddress);
        }

        // then
        assertThatThrownBy(() ->
                rateLimiter.check("user5@example.com", ipAddress)
        ).isInstanceOf(PasswordResetRateLimitExceededException.class);
    }
}
