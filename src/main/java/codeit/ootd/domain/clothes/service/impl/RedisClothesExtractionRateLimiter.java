package codeit.ootd.domain.clothes.service.impl;

import codeit.ootd.domain.clothes.service.ClothesExtractionRateLimiter;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 사용자별 고정 구간 카운터로 구매 링크 불러오기 호출 횟수를 제한한다.
 * 외부 사이트로 요청을 보내는 기능이라 반복 호출을 막기 위한 보조 장치이므로,
 * Redis에 문제가 있으면 기능을 멈추지 않고 요청을 통과시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisClothesExtractionRateLimiter implements ClothesExtractionRateLimiter {

    private static final String KEY_PREFIX = "clothes-extraction:rate:user:";
    private static final long WINDOW_MS = 60_000;
    private static final int LIMIT = 10;

    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            if count > tonumber(ARGV[2]) then
                return 0
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void check(UUID userId) {
        if (userId == null) {
            // 사용자 없이 호출되면 모든 요청이 같은 키를 공유하게 되므로 횟수를 세지 않고 막는다.
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        Long result;
        try {
            result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(KEY_PREFIX + userId),
                    String.valueOf(WINDOW_MS),
                    String.valueOf(LIMIT));
        } catch (DataAccessException e) {
            log.warn("구매 링크 요청 횟수 제한 확인 실패, 요청을 통과시킵니다: userId={}, reason={}", userId, e.toString());
            return;
        }

        if (result == null) {
            log.warn("구매 링크 요청 횟수 제한 결과가 없어 요청을 통과시킵니다: userId={}", userId);
            return;
        }
        if (result == 0L) {
            throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_RATE_LIMIT_EXCEEDED);
        }
    }
}
