package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.InvalidAuthSessionException;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.auth.service.impl.AuthSessionServiceImpl;
import codeit.ootd.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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

    @Test
    @DisplayName("유효한 인증 세션을 조회한다")
    void getActiveSession_success() {
        // given
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AuthSession session = mock(AuthSession.class);

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session));

        when(session.isActive(now)).thenReturn(true);

        // when
        AuthSession result = authSessionService.getActiveSession(sessionId);

        // then
        assertSame(session, result);

        verify(authSessionRepository).findById(sessionId);
        verify(session).isActive(now);
    }

    @Test
    @DisplayName("존재하지 않는 인증 세션 조회 시 예외가 발생한다")
    void getActiveSession_notFound() {
        // given
        UUID sessionId = UUID.randomUUID();

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.empty());

        // when & then
        InvalidAuthSessionException exception = assertThrows(
                InvalidAuthSessionException.class,
                () -> authSessionService.getActiveSession(sessionId)
        );

        assertEquals(
                ErrorCode.INVALID_AUTH_SESSION.getMessage(),
                exception.getMessage()
        );

        verify(authSessionRepository).findById(sessionId);
    }


    @ParameterizedTest
    @EnumSource(value = SessionState.class, names = {"EXPIRED", "REVOKED"})
    @DisplayName("만료되거나 폐기된 인증 세션은 조회를 거부한다")
    void getActiveSession_inactiveSession(SessionState state) {
        // given
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Instant expiresAt = state == SessionState.EXPIRED
                ? now.minusSeconds(1)
                : now.plus(Duration.ofDays(1));

        AuthSession session = AuthSession.create(userId, expiresAt);

        if (state == SessionState.REVOKED) {
            session.revoke(now.minusSeconds(10));
        }

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session));

        // when & then
        InvalidAuthSessionException exception = assertThrows(
                InvalidAuthSessionException.class,
                () -> authSessionService.getActiveSession(sessionId)
        );

        assertEquals(
                ErrorCode.INVALID_AUTH_SESSION.getMessage(),
                exception.getMessage()
        );

        verify(authSessionRepository).findById(sessionId);
    }

    @Test
    @DisplayName("로그아웃 시 활성 인증 세션을 폐기한다")
    void revokeSession_success() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        AuthSession session = AuthSession.create(
                userId,
                now.plus(Duration.ofDays(1))
        );

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session));

        // when
        authSessionService.revokeSession(sessionId);

        // then
        assertEquals(now, session.getRevokedAt());
        assertFalse(session.isActive(now));

        verify(authSessionRepository).findById(sessionId);
    }

    @Test
    @DisplayName("존재하지 않는 인증 세션을 폐기하면 예외가 발생한다")
    void revokeSession_notFound() {
        // given
        UUID sessionId = UUID.randomUUID();

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.empty());

        // when & then
        InvalidAuthSessionException exception = assertThrows(
                InvalidAuthSessionException.class,
                () -> authSessionService.revokeSession(sessionId)
        );

        assertEquals(
                ErrorCode.INVALID_AUTH_SESSION,
                exception.getErrorCode()
        );

        verify(authSessionRepository).findById(sessionId);
    }

    @Test
    @DisplayName("이미 폐기된 인증 세션은 다시 폐기해도 기존 폐기 시간을 유지한다")
    void revokeSession_alreadyRevoked() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        AuthSession session = AuthSession.create(
                userId,
                now.plus(Duration.ofDays(1))
        );

        Instant firstRevokedAt = now.minus(Duration.ofMinutes(10));
        session.revoke(firstRevokedAt);

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session));

        // when
        authSessionService.revokeSession(sessionId);

        // then
        assertEquals(firstRevokedAt, session.getRevokedAt());
        assertFalse(session.isActive(now));

        verify(authSessionRepository).findById(sessionId);
    }

    @Test
    @DisplayName("만료된 인증 세션도 폐기 처리할 수 있다")
    void revokeSession_expiredSession() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        AuthSession session = AuthSession.create(
                userId,
                now.minus(Duration.ofMinutes(1))
        );

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session));

        // when
        authSessionService.revokeSession(sessionId);

        // then
        assertEquals(now, session.getRevokedAt());
        assertFalse(session.isActive(now));

        verify(authSessionRepository).findById(sessionId);
    }

    private enum SessionState {
        EXPIRED,
        REVOKED
    }

}
