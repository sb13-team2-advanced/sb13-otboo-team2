package codeit.ootd.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.InvalidAuthSessionException;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class StompJwtAuthenticationInterceptorTest {

    @Mock JwtProvider jwtProvider;
    @Mock AuthSessionService authSessionService;
    @Mock UserRepository userRepository;
    @Mock AuthSessionWebSocketRegistry webSocketSessions;
    @InjectMocks StompJwtAuthenticationInterceptor interceptor;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @Test
    @DisplayName("유효한 JWT와 활성 계정이면 연결 사용자와 권한을 설정한다")
    void connect_authenticatesActiveUser() {
        stubClaims();
        stubSession(userId);
        User user = stubUser();
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRole()).thenReturn(UserRole.USER);
        StompHeaderAccessor accessor = connect("Bearer valid");

        interceptor.preSend(message(accessor), null);

        Authentication authentication = (Authentication) accessor.getUser();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(userId);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @DisplayName("인증 헤더가 없거나 토큰이 비어 있으면 연결을 거부한다")
    @NullSource
    @ValueSource(strings = {"Bearer "})
    void connect_rejectsMissingBearerToken(String header) {
        assertRejected(connect(header));
        verifyNoInteractions(jwtProvider, authSessionService, userRepository);
    }

    @Test
    @DisplayName("JWT 검증에 실패하면 연결을 거부한다")
    void connect_rejectsInvalidOrExpiredJwt() {
        when(jwtProvider.parseAccessToken("valid")).thenThrow(new JwtException("invalid"));
        assertRejected(connect("Bearer valid"));
    }

    @Test
    @DisplayName("폐기된 로그인 세션이면 연결을 거부한다")
    void connect_rejectsRevokedSession() {
        stubClaims();
        when(authSessionService.getActiveSession(sessionId)).thenThrow(new InvalidAuthSessionException());
        assertRejected(connect("Bearer valid"));
        // 등록 후 활성 상태를 확인해야 CONNECT와 폐기가 겹쳐도 연결이 남지 않는다.
        var order = inOrder(webSocketSessions, authSessionService);
        order.verify(webSocketSessions).bind("websocket-session", sessionId);
        order.verify(authSessionService).getActiveSession(sessionId);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("JWT 사용자와 로그인 세션 소유자가 다르면 연결을 거부한다")
    void connect_rejectsSessionOwnedByAnotherUser() {
        stubClaims();
        stubSession(UUID.randomUUID());
        assertRejected(connect("Bearer valid"));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("사용자가 존재하지 않으면 연결을 거부한다")
    void connect_rejectsMissingUser() {
        stubClaims();
        stubSession(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertRejected(connect("Bearer valid"));
    }

    @Test
    @DisplayName("잠긴 계정이면 연결을 거부한다")
    void connect_rejectsLockedUser() {
        stubClaims();
        stubSession(userId);
        when(stubUser().isLocked()).thenReturn(true);
        assertRejected(connect("Bearer valid"));
    }

    @Test
    @DisplayName("활성 상태가 아닌 계정이면 연결을 거부한다")
    void connect_rejectsInactiveUser() {
        stubClaims();
        stubSession(userId);
        when(stubUser().getStatus()).thenReturn(UserStatus.DEACTIVATED);
        assertRejected(connect("Bearer valid"));
    }

    @Test
    @DisplayName("CONNECT 없이 후속 메시지의 인증 헤더만 보내면 거부한다")
    void nonConnect_rejectsWithoutConnectedSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setNativeHeader("Authorization", "Bearer valid");
        assertRejected(accessor);
        verifyNoInteractions(jwtProvider, authSessionService, userRepository);
    }

    @Test
    @DisplayName("후속 메시지는 헤더의 새 토큰 대신 연결 당시 토큰과 세션을 재검증한다")
    void nonConnect_revalidatesConnectedToken() {
        StompHeaderAccessor connected = connected();
        StompHeaderAccessor accessor = subsequent(connected, StompCommand.SEND);
        accessor.setNativeHeader("Authorization", "Bearer another-token");
        Message<?> message = message(accessor);

        assertThat(interceptor.preSend(message, null)).isSameAs(message);

        assertThat(accessor.getUser()).isSameAs(connected.getUser());
        verify(jwtProvider, times(2)).parseAccessToken("valid");
        verify(jwtProvider, never()).parseAccessToken("another-token");
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"SEND", "SUBSCRIBE"})
    @DisplayName("연결 이후 로그아웃 등으로 인증 세션이 폐기되면 후속 메시지를 거부한다")
    void nonConnect_rejectsRevokedSession(StompCommand command) {
        StompHeaderAccessor connected = connected();
        when(authSessionService.getActiveSession(sessionId))
                .thenThrow(new InvalidAuthSessionException());

        assertSubsequentRejected(connected, command);
    }

    @Test
    @DisplayName("연결 이후 JWT가 만료되면 후속 메시지를 거부한다")
    void nonConnect_rejectsExpiredToken() {
        StompHeaderAccessor connected = connected();
        when(jwtProvider.parseAccessToken("valid")).thenThrow(new JwtException("expired"));

        assertSubsequentRejected(connected, StompCommand.SEND);
    }

    private StompHeaderAccessor connected() {
        stubClaims();
        stubSession(userId);
        User user = stubUser();
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRole()).thenReturn(UserRole.USER);
        StompHeaderAccessor accessor = connect("Bearer valid");
        interceptor.preSend(message(accessor), null);
        return accessor;
    }

    private StompHeaderAccessor subsequent(StompHeaderAccessor connected, StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(connected.getSessionAttributes());
        accessor.setUser(connected.getUser());
        return accessor;
    }

    private void assertSubsequentRejected(StompHeaderAccessor connected, StompCommand command) {
        assertThatThrownBy(() -> interceptor.preSend(message(subsequent(connected, command)), null))
                .isInstanceOf(BadCredentialsException.class);
    }

    private void assertRejected(StompHeaderAccessor accessor) {
        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(accessor.getUser()).isNull();
    }

    private StompHeaderAccessor connect(String header) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("websocket-session");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setNativeHeader("Authorization", header);
        return accessor;
    }

    private Message<?> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void stubClaims() {
        Claims claims = mock(Claims.class);
        when(jwtProvider.parseAccessToken("valid")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("sessionId", String.class)).thenReturn(sessionId.toString());
    }

    private void stubSession(UUID ownerId) {
        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(ownerId);
        when(authSessionService.getActiveSession(sessionId)).thenReturn(session);
    }

    private User stubUser() {
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return user;
    }
}
