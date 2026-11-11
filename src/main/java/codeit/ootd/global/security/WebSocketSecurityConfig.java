package codeit.ootd.global.security;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    private static final String DM_SUBSCRIPTION_PREFIX = "/sub/direct-messages_";

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages
    ) {
        messages
                .simpSubscribeDestMatchers(DM_SUBSCRIPTION_PREFIX + "*")
                .access((authentication, context) -> new AuthorizationDecision(
                        isConversationParticipant(authentication.get(), context.getMessage())
                ))
                .simpTypeMatchers(SimpMessageType.SUBSCRIBE).denyAll()
                .simpDestMatchers("/pub/direct-messages_send").authenticated()
                .simpTypeMatchers(SimpMessageType.MESSAGE).denyAll()
                .simpTypeMatchers(
                        SimpMessageType.CONNECT,
                        SimpMessageType.UNSUBSCRIBE,
                        SimpMessageType.DISCONNECT,
                        SimpMessageType.HEARTBEAT
                ).authenticated()
                .anyMessage().denyAll();
        return messages.build();
    }

    private boolean isConversationParticipant(Authentication authentication, Message<?> message) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UUID userId)) {
            return false;
        }

        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination == null || !destination.startsWith(DM_SUBSCRIPTION_PREFIX)) {
            return false;
        }

        String[] participants = destination.substring(DM_SUBSCRIPTION_PREFIX.length()).split("_", -1);
        if (participants.length != 2) {
            return false;
        }

        try {
            UUID first = UUID.fromString(participants[0]);
            UUID second = UUID.fromString(participants[1]);
            // 프런트와 동일하게 정규 UUID 문자열을 사전순으로 정렬한 경로만 허용한다.
            return participants[0].equals(first.toString())
                    && participants[1].equals(second.toString())
                    && participants[0].compareTo(participants[1]) <= 0
                    && (userId.equals(first) || userId.equals(second));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
