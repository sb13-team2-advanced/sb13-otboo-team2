package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "jwt.access-token-expiration=30m"
})
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("JWT 발급 실패 시 기존 세션 무효화와 새 세션 생성이 롤백된다")
    void signIn_jwtFailure_rollsBackSessionChanges() {
        // given
        String email = "rollback-" + UUID.randomUUID() + "@example.com";
        String password = "password123!";

        User user = userRepository.save(
                User.create(
                        "롤백테스트",
                        email,
                        passwordEncoder.encode(password)
                )
        );

        UUID userId = user.getId();
        Instant now = clock.instant();

        AuthSession existingSession = authSessionRepository.save(
                AuthSession.create(
                        userId,
                        now.plus(Duration.ofDays(1))
                )
        );

        UUID existingSessionId = existingSession.getId();

        when(jwtProvider.generateAccessToken(any(UUID.class), any(UUID.class)))
                .thenThrow(new IllegalStateException("JWT 발급 실패"));

        // when & then
        assertThrows(
                IllegalStateException.class,
                () -> authService.signIn(email, password)
        );

        // 기존 세션은 여전히 활성 상태여야 한다.
        AuthSession reloadedSession = authSessionRepository
                .findById(existingSessionId)
                .orElseThrow();

        assertNull(reloadedSession.getRevokedAt());

        // 새로운 세션이 DB에 남아 있으면 안 된다.
        List<AuthSession> sessions = authSessionRepository
                .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        userId, clock.instant()
                );

        assertEquals(1, sessions.size());
        assertEquals(existingSessionId, sessions.get(0).getId());

        verify(jwtProvider).generateAccessToken(
                eq(userId),
                any(UUID.class)
        );
    }
}
