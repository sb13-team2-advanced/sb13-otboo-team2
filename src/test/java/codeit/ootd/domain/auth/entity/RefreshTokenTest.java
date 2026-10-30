package codeit.ootd.domain.auth.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest {

    @Test
    @DisplayName("세션 ID와 토큰 해시, 만료 시간으로 Refresh Token을 생성한다")
    void create_success() {
        // given
        UUID sessionId = UUID.randomUUID();
        String tokenHash = "hashed-refresh-token";
        Instant expiresAt =
                Instant.parse("2026-11-03T06:00:00Z");

        // when
        RefreshToken refreshToken = RefreshToken.create(
                sessionId,
                tokenHash,
                expiresAt
        );

        // then
        assertAll(
                () -> assertEquals(
                        sessionId,
                        refreshToken.getSessionId()
                ),
                () -> assertEquals(
                        tokenHash,
                        refreshToken.getTokenHash()
                ),
                () -> assertEquals(
                        expiresAt,
                        refreshToken.getExpiresAt()
                )
        );
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("세션 ID가 null이면 RefreshToken을 생성할 수 없다")
    void create_nullSessionId_throwsException(UUID sessionId) {
        // given
        String tokenHash = "hashed-refresh-token";
        Instant expiresAt = Instant.parse("2026-11-01T00:00:00Z");

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> RefreshToken.create(sessionId, tokenHash, expiresAt)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("토큰 해시가 null이거나 비어 있으면 생성할 수 없다")
    void create_invalidTokenHash_throwsException(String tokenHash) {
        // given
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-11-01T00:00:00Z");

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> RefreshToken.create(sessionId, tokenHash, expiresAt)
        );
    }

    @Test
    @DisplayName("만료 시간이 null이면 RefreshToken을 생성할 수 없다")
    void create_nullExpiresAt_throwsException() {
        // given
        UUID sessionId = UUID.randomUUID();
        String tokenHash = "hashed-refresh-token";

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> RefreshToken.create(sessionId, tokenHash, null)
        );
    }

}
