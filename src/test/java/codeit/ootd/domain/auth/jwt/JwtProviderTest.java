package codeit.ootd.domain.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtProviderTest {

    private final SecretKey secretKey = Jwts.SIG.HS256.key().build();

    private final JwtProvider jwtProvider =
            new JwtProvider(secretKey, Duration.ofMinutes(30));

    @Test
    @DisplayName("사용자 ID와 세션 ID로 Access Token을 발급한다")
    void generateAccessToken_success() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        // when
        String accessToken = jwtProvider.generateAccessToken(
                userId,
                sessionId
        );

        // then
        assertNotNull(accessToken);
        assertFalse(accessToken.isBlank());
        assertEquals(3, accessToken.split("\\.").length);
    }

    @Test
    @DisplayName("발급된 Access Token에 사용자 ID와 세션 ID가 포함된다")
    void generateAccessToken_containsClaims() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        // when
        String accessToken = jwtProvider.generateAccessToken(userId, sessionId);

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();

        // then
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(sessionId.toString(), claims.get("sessionId", String.class));

        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));

        long expirationSeconds =
                (claims.getExpiration().getTime()
                        - claims.getIssuedAt().getTime()) / 1000;

        assertEquals(Duration.ofMinutes(30).toSeconds(), expirationSeconds);
    }

    @Test
    @DisplayName("유효한 Access Token을 검증하고 사용자 ID와 세션 ID를 추출한다")
    void parseAccessToken_success() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String accessToken = jwtProvider.generateAccessToken(
                userId, sessionId
        );

        // when
        Claims claims = jwtProvider.parseAccessToken(accessToken);

        // then
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(
                sessionId.toString(),
                claims.get("sessionId", String.class)
        );
    }

    @Test
    @DisplayName("만료된 Access Token은 검증에 실패한다")
    void parseAccessToken_expired_throwsException() {
        // given
        Instant now = Instant.now();

        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("sessionId", UUID.randomUUID().toString())
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(secretKey)
                .compact();

        // when & then
        assertThrows(
                ExpiredJwtException.class,
                () -> jwtProvider.parseAccessToken(expiredToken)
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -30})
    @DisplayName("Access Token 만료 시간이 0 이하이면 생성에 실패한다")
    void createJwtProvider_invalidExpiration_throwsException(long seconds) {
        // given
        Duration expiration = Duration.ofSeconds(seconds);

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProvider(secretKey, expiration)
        );
    }

    @Test
    @DisplayName("Access Token 만료 시간이 null이면 생성에 실패한다")
    void createJwtProvider_nullExpiration_throwsException() {
        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProvider(secretKey, null)
        );
    }

    @Test
    @DisplayName("다른 비밀키로 서명된 Access Token은 검증에 실패한다")
    void parseAccessToken_invalidSignature_throwsException() {
        // given
        SecretKey anotherSecretKey = Jwts.SIG.HS256.key().build();

        JwtProvider anotherJwtProvider = new JwtProvider(
                anotherSecretKey,
                Duration.ofMinutes(30)
        );

        String accessToken = createAccessToken(anotherJwtProvider);

        // when & then
        assertThrows(
                SignatureException.class,
                () -> jwtProvider.parseAccessToken(accessToken)
        );
    }

    @Test
    @DisplayName("Payload가 변조된 Access Token은 서명 검증에 실패한다")
    void parseAccessToken_tampered_throwsException() {
        // given
        String accessToken = createAccessToken(jwtProvider);

        String[] parts = accessToken.split("\\.");

        String payload = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
        );

        String tamperedPayload = payload.replace(
                "\"sessionId\":",
                "\"otherSessionId\":"
        );

        assertNotEquals(payload, tamperedPayload);

        parts[1] = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8));

        String tamperedToken = String.join(".", parts);

        // when & then
        assertThrows(
                SignatureException.class,
                () -> jwtProvider.parseAccessToken(tamperedToken)
        );
    }

    @Test
    @DisplayName("잘못된 형식의 Access Token은 검증에 실패한다")
    void parseAccessToken_malformed_throwsException() {
        // given
        String malformedToken = "invalid.jwt.token";

        // when & then
        assertThrows(
                JwtException.class,
                () -> jwtProvider.parseAccessToken(malformedToken)
        );
    }

    @Test
    @DisplayName("JWT 비밀키가 null이면 생성에 실패한다")
    void createJwtProvider_nullSecretKey_throwsException() {
        // when & then
        assertThrows(
                NullPointerException.class,
                () -> new JwtProvider(null, Duration.ofMinutes(30))
        );
    }

    private String createAccessToken(JwtProvider provider) {
        return provider.generateAccessToken(
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }

}
