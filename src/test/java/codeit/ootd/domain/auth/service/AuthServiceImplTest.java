package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.dto.response.TokenRefreshResult;
import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.AccountInactiveException;
import codeit.ootd.domain.auth.exception.AccountLockedException;
import codeit.ootd.domain.auth.exception.InvalidAuthSessionException;
import codeit.ootd.domain.auth.exception.InvalidCredentialsException;
import codeit.ootd.domain.auth.exception.InvalidRefreshTokenException;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.impl.AuthServiceImpl;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;

import codeit.ootd.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("이메일과 비밀번호가 일치하면 인증에 성공한다")
    void authenticate_success() {
        // given
        String email = "test@example.com";
        String rawPassword = "password123!";
        String encodedPassword = "encoded-password";

        User user = createUser(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                rawPassword,
                encodedPassword
        )).thenReturn(true);

        // when
        User result = authService.authenticate(
                email,
                rawPassword
        );

        // then
        assertSame(user, result);

        verify(userRepository).findByEmail(email);
        verify(passwordEncoder).matches(
                rawPassword,
                encodedPassword
        );
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인하면 인증에 실패한다")
    void authenticate_emailNotFound_throwsException() {
        // given
        String email = "unknown@example.com";
        String password = "password123!";

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate(email, password)
        );

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 인증에 실패한다")
    void authenticate_wrongPassword_throwsException() {
        // given
        String email = "test@example.com";
        String password = "wrongPassword";

        User user = createUser(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(password, user.getPassword()))
                .thenReturn(false);

        // when & then
        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate(email, password)
        );

        verify(passwordEncoder).matches(
                password,
                user.getPassword()
        );
    }

    @Test
    @DisplayName("잠긴 계정은 비밀번호가 일치해도 로그인할 수 없다")
    void authenticate_lockedUser_throwsException() {
        // given
        String email = "test@example.com";
        String password = "password123!";

        User user = createUser(email);
        user.lock();

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(password, user.getPassword()))
                .thenReturn(true);

        // when & then
        assertThrows(
                AccountLockedException.class,
                () -> authService.authenticate(email, password)
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"DEACTIVATED", "DELETED"}
    )
    @DisplayName("비활성화되거나 탈퇴한 계정은 로그인할 수 없다")
    void authenticate_inactiveUser_throwsException(UserStatus status) {
        // given
        String email = "test@example.com";
        String password = "password123!";

        User user = createUser(email);

        if (status == UserStatus.DEACTIVATED) {
            user.deactivate();
        } else {
            user.markDeleted();
        }

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(password, user.getPassword()))
                .thenReturn(true);

        // when & then
        assertThrows(
                AccountInactiveException.class,
                () -> authService.authenticate(email, password)
        );
    }

    @Test
    @DisplayName("로그인 성공 시 세션을 생성하고 Access/Refresh Token을 발급한다")
    void signIn_success() {
        // given
        String email = "test@example.com";
        String password = "password123!";
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User user = mock(User.class);
        AuthSession session = mock(AuthSession.class);

        when(user.getId()).thenReturn(userId);
        when(user.getPassword()).thenReturn("encoded-password");
        when(user.isLocked()).thenReturn(false);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(password, "encoded-password"))
                .thenReturn(true);

        when(authSessionService.createSession(userId))
                .thenReturn(session);

        when(session.getId()).thenReturn(sessionId);

        when(jwtProvider.generateAccessToken(userId, sessionId))
                .thenReturn("test-access-token");

        when(refreshTokenService.issue(sessionId))
                .thenReturn("test-refresh-token");

        // when
        SignInResult result = authService.signIn(email, password);

        // then
        assertAll(
                () -> assertNotNull(result.userDto()),
                () -> assertEquals("test-access-token", result.accessToken()),
                () -> assertEquals("test-refresh-token", result.refreshToken())
        );

        InOrder inOrder = inOrder(
                authSessionService,
                jwtProvider,
                refreshTokenService
        );

        inOrder.verify(authSessionService).createSession(userId);
        inOrder.verify(jwtProvider).generateAccessToken(userId, sessionId);
        inOrder.verify(refreshTokenService).issue(sessionId);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 세션과 Access Token을 생성하지 않는다")
    void signIn_wrongPassword_doesNotCreateSessionOrToken() {
        // given
        String email = "test@example.com";
        String password = "wrong-password";

        User user = createUser(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(password, user.getPassword()))
                .thenReturn(false);

        // when & then
        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.signIn(email, password)
        );

        verifyNoInteractions(authSessionService, jwtProvider, refreshTokenService);
    }


    @Test
    @DisplayName("유효한 Refresh Token으로 토큰 재발급에 성공한다")
    void refresh_success() {
        // given
        RefreshFixture f = createRefreshFixture();

        when(userRepository.findById(f.userId()))
                .thenReturn(Optional.of(f.user()));

        when(f.user().getId()).thenReturn(f.userId());
        when(f.user().getStatus()).thenReturn(UserStatus.ACTIVE);

        when(refreshTokenService.rotate(
                f.sessionId(), f.oldRefreshToken()
        )).thenReturn("new-refresh-token");

        when(jwtProvider.generateAccessToken(
                f.userId(), f.sessionId()
        )).thenReturn("new-access-token");

        // when
        TokenRefreshResult result = authService.refresh(f.oldRefreshToken());

        // then
        assertEquals("new-access-token", result.accessToken());
        assertEquals("new-refresh-token", result.refreshToken());

        verify(refreshTokenService)
                .rotate(f.sessionId(), f.oldRefreshToken());

        verify(authSessionService, never()).createSession(any());
    }

    @Test
    @DisplayName("잠긴 계정은 Refresh Token 재발급을 거부한다")
    void refresh_lockedAccount() {
        // given
        RefreshFixture f = createRefreshFixture();

        when(userRepository.findById(f.userId()))
                .thenReturn(Optional.of(f.user()));

        when(f.user().isLocked()).thenReturn(true);

        // when & then
        assertThrows(
                AccountLockedException.class,
                () -> authService.refresh(f.oldRefreshToken())
        );

        verify(refreshTokenService, never())
                .rotate(any(), anyString());

        verify(jwtProvider, never())
                .generateAccessToken(any(), any());
    }


    @Test
    @DisplayName("비활성 계정은 Refresh Token 재발급을 거부한다")
    void refresh_inactiveAccount() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String oldRefreshToken = "old-refresh-token";

        User user = mock(User.class);
        AuthSession session = mock(AuthSession.class);

        when(refreshTokenService.findSessionIdByToken(oldRefreshToken))
                .thenReturn(sessionId);

        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        when(session.getUserId()).thenReturn(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(user.isLocked()).thenReturn(false);
        when(user.getStatus()).thenReturn(UserStatus.DEACTIVATED);

        // when & then
        assertThrows(
                AccountInactiveException.class,
                () -> authService.refresh(oldRefreshToken)
        );

        // 비활성 계정은 토큰을 교체하거나 발급하면 안 됨
        verify(refreshTokenService, never())
                .rotate(any(), anyString());

        verify(jwtProvider, never())
                .generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("사용자가 존재하지 않으면 토큰 재발급을 거부한다")
    void refresh_userNotFound() {
        // given
        RefreshFixture f = createRefreshFixture();

        when(userRepository.findById(f.userId()))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> authService.refresh(f.oldRefreshToken())
        );

        verify(refreshTokenService, never())
                .rotate(any(), anyString());

        verify(jwtProvider, never())
                .generateAccessToken(any(), any());
    }


    @Test
    @DisplayName("Refresh Token 회전에 실패하면 Access Token을 발급하지 않는다")
    void refresh_rotateFailed() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String oldRefreshToken = "old-refresh-token";

        User user = mock(User.class);
        AuthSession session = mock(AuthSession.class);

        when(refreshTokenService.findSessionIdByToken(oldRefreshToken))
                .thenReturn(sessionId);

        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        when(session.getUserId()).thenReturn(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(user.isLocked()).thenReturn(false);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);

        when(refreshTokenService.rotate(sessionId, oldRefreshToken))
                .thenThrow(new InvalidRefreshTokenException());

        // when & then
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(oldRefreshToken)
        );

        verify(refreshTokenService)
                .rotate(sessionId, oldRefreshToken);

        verify(jwtProvider, never())
                .generateAccessToken(any(), any());

        verify(authSessionService, never())
                .createSession(any());
    }

    private User createUser(String email) {
        return User.create(
                "테스트사용자",
                email,
                "encoded-password"
        );
    }


    private record RefreshFixture(
            UUID userId,
            UUID sessionId,
            String oldRefreshToken,
            User user
    ) {
    }

    private RefreshFixture createRefreshFixture() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String oldRefreshToken = "old-refresh-token";

        User user = mock(User.class);
        AuthSession session = mock(AuthSession.class);

        when(refreshTokenService.findSessionIdByToken(oldRefreshToken))
                .thenReturn(sessionId);

        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        when(session.getUserId()).thenReturn(userId);

        return new RefreshFixture(
                userId,
                sessionId,
                oldRefreshToken,
                user
        );
    }

    @Test
    @DisplayName("유효하지 않은 인증 세션은 Refresh Token 재발급을 거부한다")
    void refresh_invalidSession() {
        // given
        String oldRefreshToken = "old-refresh-token";
        UUID sessionId = UUID.randomUUID();

        when(refreshTokenService.findSessionIdByToken(oldRefreshToken))
                .thenReturn(sessionId);

        when(authSessionService.getActiveSession(sessionId))
                .thenThrow(new InvalidAuthSessionException());

        // when & then
        InvalidRefreshTokenException exception = assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(oldRefreshToken)
        );

        assertEquals(
                ErrorCode.INVALID_REFRESH_TOKEN,
                exception.getErrorCode()
        );

        verify(refreshTokenService, never())
                .rotate(any(), anyString());

        verify(jwtProvider, never())
                .generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("유효한 Refresh Token으로 로그아웃하면 인증 세션을 폐기한다")
    void signOut_success() {
        // given
        String refreshToken = "valid-refresh-token";
        UUID sessionId = UUID.randomUUID();

        when(refreshTokenService.findSessionIdByToken(refreshToken))
                .thenReturn(sessionId);

        // when
        authService.signOut(refreshToken);

        // then
        verify(refreshTokenService).findSessionIdByToken(refreshToken);
        verify(authSessionService).revokeSession(sessionId);

        verify(authSessionService, never()).createSession(any());
        verify(refreshTokenService, never()).rotate(any(), anyString());
        verify(jwtProvider, never())
                .generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token으로 로그아웃해도 정상 종료한다")
    void signOut_invalidRefreshToken() {
        // given
        String refreshToken = "invalid-refresh-token";

        when(refreshTokenService.findSessionIdByToken(refreshToken))
                .thenThrow(new InvalidRefreshTokenException());

        // when & then
        assertDoesNotThrow(() -> authService.signOut(refreshToken));

        verify(authSessionService, never()).revokeSession(any());
    }

    @Test
    @DisplayName("인증 세션이 존재하지 않아도 로그아웃은 정상 종료한다")
    void signOut_invalidSession() {
        // given
        String refreshToken = "valid-refresh-token";
        UUID sessionId = UUID.randomUUID();

        when(refreshTokenService.findSessionIdByToken(refreshToken))
                .thenReturn(sessionId);

        doThrow(new InvalidAuthSessionException())
                .when(authSessionService)
                .revokeSession(sessionId);

        // when & then
        assertDoesNotThrow(() -> authService.signOut(refreshToken));

        verify(refreshTokenService).findSessionIdByToken(refreshToken);
        verify(authSessionService).revokeSession(sessionId);
    }



}
