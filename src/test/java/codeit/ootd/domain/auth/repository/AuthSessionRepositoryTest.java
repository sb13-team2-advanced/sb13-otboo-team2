package codeit.ootd.domain.auth.repository;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "jwt.access-token-expiration=30m"
})
@ActiveProfiles("test")
@Transactional
class AuthSessionRepositoryTest {

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("사용자의 만료되지 않은 활성 세션만 조회한다")
    void findActiveSessions_success() {
        // given
        User user = userRepository.save(
                User.create(
                        "테스트사용자",
                        "session-test@example.com",
                        "encoded-password"
                )
        );

        Instant now = Instant.parse("2026-10-27T06:00:00Z");

        AuthSession activeSession = AuthSession.create(
                user.getId(),
                now.plusSeconds(3600)
        );

        AuthSession expiredSession = AuthSession.create(
                user.getId(),
                now.minusSeconds(3600)
        );

        AuthSession revokedSession = AuthSession.create(
                user.getId(),
                now.plusSeconds(3600)
        );
        revokedSession.revoke(now.minusSeconds(60));

        authSessionRepository.saveAll(
                List.of(activeSession, expiredSession, revokedSession)
        );

        // when
        List<AuthSession> result =
                authSessionRepository
                        .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                                user.getId(),
                                now
                        );

        // then
        assertEquals(1, result.size());
        assertEquals(activeSession.getId(), result.get(0).getId());
    }

    @Test
    @DisplayName("다른 사용자의 활성 세션은 조회되지 않는다")
    void findActiveSessions_otherUser_excluded() {
        // given
        User user1 = userRepository.save(
                User.create("사용자1", "user1@example.com", "encoded-password")
        );
        User user2 = userRepository.save(
                User.create("사용자2", "user2@example.com", "encoded-password")
        );

        Instant now = Instant.parse("2026-10-27T06:00:00Z");

        AuthSession session1 = AuthSession.create(
                user1.getId(), now.plusSeconds(3600)
        );
        AuthSession session2 = AuthSession.create(
                user2.getId(), now.plusSeconds(3600)
        );

        authSessionRepository.saveAllAndFlush(
                List.of(session1, session2)
        );

        // when
        List<AuthSession> result = authSessionRepository
                .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        user1.getId(), now
                );

        // then
        assertEquals(1, result.size());
        assertEquals(session1.getId(), result.get(0).getId());
    }

    @Test
    @DisplayName("만료 시각이 현재 시각과 같으면 활성 세션 조회에서 제외한다")
    void findActiveSessions_expiresAtEqualsNow_excluded() {
        // given
        User user = userRepository.save(
                User.create(
                        "테스트사용자",
                        "boundary@example.com",
                        "encoded-password"
                )
        );

        Instant now = Instant.parse("2026-10-27T06:00:00Z");

        AuthSession session = AuthSession.create(
                user.getId(),
                now
        );

        authSessionRepository.saveAndFlush(session);

        // when
        List<AuthSession> result = authSessionRepository
                .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        user.getId(),
                        now
                );

        // then
        assertTrue(result.isEmpty());
    }
}