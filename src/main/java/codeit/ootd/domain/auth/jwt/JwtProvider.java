package codeit.ootd.domain.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class JwtProvider {

    private final SecretKey secretKey;
    private final Duration accessTokenExpiration;


    public JwtProvider(SecretKey secretKey, Duration accessTokenExpiration) {
        if (accessTokenExpiration == null
                || accessTokenExpiration.isZero()
                || accessTokenExpiration.isNegative()) {
            throw new IllegalArgumentException(
                    "Access Token 만료 시간은 0보다 커야 합니다."
            );
        }

        this.secretKey = Objects.requireNonNull(secretKey, "JWT 비밀키는 필수입니다.");
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String generateAccessToken(UUID userId, UUID sessionId) {
        Instant now = Instant.now();

        String accessToken = Jwts.builder()
                .subject(userId.toString())
                .claim("sessionId", sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiration)))
                .signWith(secretKey)
                .compact();

        log.debug("Access Token 발급 완료: sessionId={}", sessionId);

        return accessToken;
    }

    public Claims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        log.debug("Access Token 검증 성공");

        return claims;
    }
}
