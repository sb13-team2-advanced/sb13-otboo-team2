package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.exception.PasswordResetRateLimitExceededException;
import codeit.ootd.domain.auth.service.impl.RedisPasswordResetRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

class RedisPasswordResetRateLimiterTest {

    private final StringRedisTemplate redisTemplate =
            mock(StringRedisTemplate.class);

    private final RedisPasswordResetRateLimiter rateLimiter =
            new RedisPasswordResetRateLimiter(redisTemplate);


    @Test
    @DisplayName("Redis가 요청 제한 결과를 반환하면 예외가 발생한다")
    void check_rateLimitExceeded() {
        // given
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(0L);

        // when & then
        assertThatThrownBy(() ->
                rateLimiter.check("test@example.com", "127.0.0.1")
        ).isInstanceOf(
                PasswordResetRateLimitExceededException.class
        );
    }

    @Test
    @DisplayName("Redis 실행 결과가 없으면 요청을 허용하지 않는다")
    void check_redisResultNull() {
        // given
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(null);

        // when & then
        assertThatThrownBy(() ->
                rateLimiter.check("test@example.com", "127.0.0.1")
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Redis가 요청을 허용하면 정상적으로 처리한다")
    void check_firstRequest_allowed() {
        // given
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(1L);

        // when & then
        assertThatCode(() ->
                rateLimiter.check("test@example.com", "127.0.0.1")
        ).doesNotThrowAnyException();
    }








}
