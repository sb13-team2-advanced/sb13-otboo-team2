package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisClothesExtractionRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final RedisClothesExtractionRateLimiter rateLimiter =
            new RedisClothesExtractionRateLimiter(redisTemplate);

    private final UUID userId = UUID.randomUUID();

    @Test
    void Redis가_허용하면_예외_없이_통과한다() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1L);

        assertThatCode(() -> rateLimiter.check(userId)).doesNotThrowAnyException();
    }

    @Test
    void Redis가_한도_초과를_알리면_429_예외를_던진다() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L);

        assertThatThrownBy(() -> rateLimiter.check(userId))
                .isInstanceOfSatisfying(OtbooException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_RATE_LIMIT_EXCEEDED));
    }

    @Test
    void Redis_결과가_없으면_요청을_통과시킨다() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(null);

        assertThatCode(() -> rateLimiter.check(userId)).doesNotThrowAnyException();
    }

    @Test
    void Redis에_연결할_수_없으면_요청을_통과시킨다() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("연결 실패"));

        assertThatCode(() -> rateLimiter.check(userId)).doesNotThrowAnyException();
    }

    @Test
    void 사용자_ID가_없으면_인증_오류를_던지고_Redis를_호출하지_않는다() {
        assertThatThrownBy(() -> rateLimiter.check(null))
                .isInstanceOfSatisfying(OtbooException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));

        verifyNoInteractions(redisTemplate);
    }
}
