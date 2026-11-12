package codeit.ootd.global.security;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.OtbooException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Component
@RequiredArgsConstructor
public class StompJwtAuthenticationInterceptor implements ChannelInterceptor {

    private static final String ACCESS_TOKEN_ATTRIBUTE =
            StompJwtAuthenticationInterceptor.class.getName() + ".accessToken";

    private final JwtProvider jwtProvider;
    private final AuthSessionService authSessionService;
    private final UserRepository userRepository;
    private final AuthSessionWebSocketRegistry webSocketSessions;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class
        );
        if (accessor == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            String token = getConnectToken(accessor);
            if (accessor.getSessionAttributes() == null || accessor.getSessionId() == null) {
                throw new BadCredentialsException("STOMP 세션 정보가 없습니다.");
            }
            var authentication = authenticate(token, accessor.getSessionId());
            accessor.setUser(authentication);
            accessor.getSessionAttributes().put(ACCESS_TOKEN_ATTRIBUTE, token);
        } else if (accessor.getCommand() == StompCommand.SEND
                || accessor.getCommand() == StompCommand.SUBSCRIBE) {
            // 후속 메시지의 헤더로 인증을 바꾸지 않고, CONNECT에서 검증한 토큰을 재검증한다.
            Object token = accessor.getSessionAttributes() == null ? null
                    : accessor.getSessionAttributes().get(ACCESS_TOKEN_ATTRIBUTE);
            if (!(token instanceof String accessToken) || accessor.getUser() == null) {
                throw new BadCredentialsException("인증된 STOMP 연결이 필요합니다.");
            }
            var authentication = authenticate(accessToken, null);
            if (!authentication.getName().equals(accessor.getUser().getName())) {
                throw new BadCredentialsException("STOMP 연결 사용자가 일치하지 않습니다.");
            }
        }

        return message;
    }

    private String getConnectToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            throw new BadCredentialsException("STOMP 연결에 액세스 토큰이 필요합니다.");
        }
        return authorization.substring(7);
    }

    private UsernamePasswordAuthenticationToken authenticate(String token, String webSocketSessionId) {
        try {
            Claims claims = jwtProvider.parseAccessToken(token);
            UUID userId = UUID.fromString(claims.getSubject());
            UUID sessionId = UUID.fromString(claims.get("sessionId", String.class));

            // 폐기 이벤트와 CONNECT가 겹쳐도 놓치지 않도록, 연결을 등록한 뒤 DB 상태를 확인한다.
            if (webSocketSessionId != null) {
                webSocketSessions.bind(webSocketSessionId, sessionId);
            }
            AuthSession session = authSessionService.getActiveSession(sessionId);
            if (!userId.equals(session.getUserId())) {
                throw new BadCredentialsException("인증 세션의 사용자가 일치하지 않습니다.");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));
            if (user.isLocked() || user.getStatus() != UserStatus.ACTIVE) {
                throw new BadCredentialsException("활성 계정만 연결할 수 있습니다.");
            }

            return new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            );
        } catch (JwtException | IllegalArgumentException | NullPointerException | OtbooException e) {
            throw new BadCredentialsException("유효하지 않은 STOMP 인증 정보입니다.", e);
        }
    }
}
