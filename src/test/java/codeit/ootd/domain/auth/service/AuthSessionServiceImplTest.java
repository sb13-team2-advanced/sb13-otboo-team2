package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.auth.service.impl.AuthSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthSessionServiceImplTest {

    private AuthSessionRepository authSessionRepository;
    private AuthSessionServiceImpl authSessionService;

    private final Instant now =
            Instant.parse("2026-10-27T06:00:00Z");

    @BeforeEach
    void setUp() {
        authSessionRepository = mock(AuthSessionRepository.class);

        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        authSessionService = new AuthSessionServiceImpl(
                authSessionRepository,
                clock
        );
    }

    @Test
    @DisplayName("기존 활성 세션이 없으면 새 세션을 생성한다")
    void createSession_noExistingSession() {
        // given
        UUID userId = UUID.randomUUID();

        when(authSessionRepository
                .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        eq(userId), eq(now)
                ))
                .thenReturn(List.of());

        when(authSessionRepository.save(any(AuthSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AuthSession result = authSessionService.createSession(userId);

        // then
        ArgumentCaptor<AuthSession> captor =
                ArgumentCaptor.forClass(AuthSession.class);

        verify(authSessionRepository).save(captor.capture());

        AuthSession savedSession = captor.getValue();

        assertSame(savedSession, result);
        assertEquals(userId, savedSession.getUserId());
        assertEquals(now.plus(Duration.ofDays(7)),
                savedSession.getExpiresAt());
        assertNull(savedSession.getRevokedAt());
    }

    @Test
    @DisplayName("기존 활성 세션이 있으면 무효화하고 새 세션을 생성한다")
    void createSession_revokesExistingSession() {
        // given
        UUID userId = UUID.randomUUID();

        AuthSession existingSession = AuthSession.create(
                userId,
                now.plus(Duration.ofDays(1))
        );

        when(authSessionRepository
                .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        userId, now
                ))
                .thenReturn(List.of(existingSession));

        when(authSessionRepository.save(any(AuthSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AuthSession newSession = authSessionService.createSession(userId);

        // then
        assertEquals(now, existingSession.getRevokedAt());

        assertNotSame(existingSession, newSession);
        assertEquals(userId, newSession.getUserId());
        assertNull(newSession.getRevokedAt());

        verify(authSessionRepository).save(any(AuthSession.class));
    }

    @Test
    @DisplayName("기존 활성 세션이 여러 개이면 모두 무효화한다")
    void createSession_revokesAllExistingSessions() {
        // given
        UUID userId = UUID.randomUUID();

        AuthSession session1 = AuthSession.create(
                userId,
                now.plus(Duration.ofDays(1))
        );

        AuthSession session2 = AuthSession.create(
                userId,
                now.plus(Duration.ofDays(2))
        );

        when(authSessionRepository
                .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        userId, now
                ))
                .thenReturn(List.of(session1, session2));

        when(authSessionRepository.save(any(AuthSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AuthSession newSession = authSessionService.createSession(userId);

        // then
        assertAll(
                () -> assertEquals(now, session1.getRevokedAt()),
                () -> assertEquals(now, session2.getRevokedAt()),
                () -> assertNull(newSession.getRevokedAt()),
                () -> assertEquals(userId, newSession.getUserId())
        );

        verify(authSessionRepository, times(1))
                .save(any(AuthSession.class));
    }


}
