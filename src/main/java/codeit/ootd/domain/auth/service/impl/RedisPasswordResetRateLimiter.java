package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.exception.PasswordResetRateLimitExceededException;
import codeit.ootd.domain.auth.service.PasswordResetRateLimiter;
import codeit.ootd.global.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RedisPasswordResetRateLimiter
        implements PasswordResetRateLimiter {

    private static final long EMAIL_WINDOW_MS = 180_000;
    private static final long IP_WINDOW_MS = 600_000;
    private static final int IP_LIMIT = 5;

    private static final RedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[1]) == 1 then
                        return 0
                    end

                    local ipCount = tonumber(
                        redis.call('GET', KEYS[2]) or '0'
                    )

                    if ipCount >= tonumber(ARGV[2]) then
                        return 0
                    end

                    redis.call('SET', KEYS[1], '1', 'PX', ARGV[1])

                    local count = redis.call('INCR', KEYS[2])

                    if count == 1 then
                        redis.call('PEXPIRE', KEYS[2], ARGV[3])
                    end

                    return 1
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void check(String email, String ipAddress) {

        String normalizedEmail = EmailNormalizer.normalize(email);

        String emailKey =
                "password-reset:{limit}:email:" + hash(normalizedEmail);

        String ipKey =
                "password-reset:{limit}:ip:" + hash(ipAddress);

        Long result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(emailKey, ipKey),
                String.valueOf(EMAIL_WINDOW_MS),
                String.valueOf(IP_LIMIT),
                String.valueOf(IP_WINDOW_MS)
        );

        if (result == null) {
            throw new IllegalStateException(
                    "Redis 요청 횟수 제한 결과가 없습니다."
            );
        }

        if (result == 0L) {
            throw new PasswordResetRateLimitExceededException();
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] bytes = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 알고리즘을 사용할 수 없습니다.",
                    e
            );
        }
    }
}
