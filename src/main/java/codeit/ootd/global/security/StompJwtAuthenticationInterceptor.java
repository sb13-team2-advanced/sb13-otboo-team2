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

    private final JwtProvider jwtProvider;
    private final AuthSessionService authSessionService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class
        );
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            throw new BadCredentialsException("STOMP 연결에 액세스 토큰이 필요합니다.");
        }

        try {
            Claims claims = jwtProvider.parseAccessToken(authorization.substring(7));
            UUID userId = UUID.fromString(claims.getSubject());
            UUID sessionId = UUID.fromString(claims.get("sessionId", String.class));

            AuthSession session = authSessionService.getActiveSession(sessionId);
            if (!userId.equals(session.getUserId())) {
                throw new BadCredentialsException("인증 세션의 사용자가 일치하지 않습니다.");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));
            if (user.isLocked() || user.getStatus() != UserStatus.ACTIVE) {
                throw new BadCredentialsException("활성 계정만 연결할 수 있습니다.");
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            ));
        } catch (JwtException | IllegalArgumentException | NullPointerException | OtbooException e) {
            throw new BadCredentialsException("유효하지 않은 STOMP 인증 정보입니다.", e);
        }

        return message;
    }
}
