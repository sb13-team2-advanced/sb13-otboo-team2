
package codeit.ootd.domain.auth.entity;

import codeit.ootd.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthSessionTest {

    @Test
    @DisplayName("로그인 세션 생성 시 사용자 ID와 만료 시간이 설정되고 무효화 상태는 null이다")
    void create_success() {
        // given
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-11-01T00:00:00Z");

        // when
        AuthSession session = AuthSession.create(userId, expiresAt);

        // then
        assertEquals(userId, session.getUserId());
        assertEquals(expiresAt, session.getExpiresAt());
        assertNull(session.getRevokedAt());
    }

    @Test
    @DisplayName("로그인 세션을 무효화하면 무효화 시간이 저장된다")
    void revoke_success() {
        // given
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-11-01T00:00:00Z");
        Instant revokedAt = Instant.parse("2026-10-27T06:00:00Z");

        AuthSession session = AuthSession.create(userId, expiresAt);

        // when
        session.revoke(revokedAt);

        // then
        assertEquals(revokedAt, session.getRevokedAt());
    }

    @Test
    @DisplayName("이미 무효화된 세션은 다시 무효화해도 최초 시간이 유지된다")
    void revoke_alreadyRevoked_keepsOriginalTime() {
        // given
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-11-01T00:00:00Z");
        Instant firstRevokedAt = Instant.parse("2026-10-27T06:00:00Z");
        Instant secondRevokedAt = Instant.parse("2026-10-27T07:00:00Z");

        AuthSession session = AuthSession.create(userId, expiresAt);
        session.revoke(firstRevokedAt);

        // when
        session.revoke(secondRevokedAt);

        // then
        assertEquals(firstRevokedAt, session.getRevokedAt());
    }

    @Test
    @DisplayName("무효화되지 않고 만료되지 않은 세션은 활성 상태이다")
    void isActive_validSession_returnsTrue() {
        // given
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-27T06:00:00Z");
        Instant expiresAt = now.plusSeconds(3600);

        AuthSession session = AuthSession.create(userId, expiresAt);

        // when
        boolean result = session.isActive(now);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("무효화된 세션은 만료 전이라도 비활성 상태이다")
    void isActive_revokedSession_returnsFalse() {
        // given
        Instant now = Instant.parse("2026-10-27T06:00:00Z");

        AuthSession session = AuthSession.create(
                UUID.randomUUID(),
                now.plusSeconds(3600)
        );
        session.revoke(now.minusSeconds(60));

        // when
        boolean result = session.isActive(now);

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("만료된 세션은 비활성 상태이다")
    void isActive_expiredSession_returnsFalse() {
        // given
        Instant now = Instant.parse("2026-10-27T06:00:00Z");

        AuthSession session = AuthSession.create(
                UUID.randomUUID(),
                now.minusSeconds(60)
        );

        // when
        boolean result = session.isActive(now);

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("현재 시각과 만료 시각이 같으면 비활성 상태이다")
    void isActive_expiresAtEqualsNow_returnsFalse() {
        // given
        Instant now = Instant.parse("2026-10-27T06:00:00Z");

        AuthSession session = AuthSession.create(
                UUID.randomUUID(),
                now
        );

        // when
        boolean result = session.isActive(now);

        // then
        assertFalse(result);
    }

}
