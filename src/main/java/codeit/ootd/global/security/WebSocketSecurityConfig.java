package codeit.ootd.global.security;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
// WebSocketConfig(+99)의 JWT 인증 인터셉터 다음에 권한 검사 인터셉터를 등록한다.
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    private static final String DM_SUBSCRIPTION_PREFIX = "/sub/direct-messages_";

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        // 채팅 컨트롤러에서 @AuthenticationPrincipal로 로그인 사용자의 UUID를 받게 한다.
        argumentResolvers.add(new AuthenticationPrincipalArgumentResolver());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 클라이언트가 보낸 STOMP 메시지에 아래의 허용·거부 규칙을 적용한다.
        AuthorizationChannelInterceptor authorizationInterceptor =
                new AuthorizationChannelInterceptor(messageAuthorizationManager());
        // 권한 검사 결과를 Spring 이벤트로 발행한다. 채팅 메시지 전송과는 별개다.
        authorizationInterceptor.setAuthorizationEventPublisher(
                new SpringAuthorizationEventPublisher(eventPublisher)
        );
        // 인증된 사용자 정보를 보안 컨텍스트에 설정한 뒤 권한을 검사한다. STOMP CSRF 검사는 제외한다.
        registration.interceptors(new SecurityContextChannelInterceptor(), authorizationInterceptor);
    }

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager() {
        MessageMatcherDelegatingAuthorizationManager.Builder messages =
                MessageMatcherDelegatingAuthorizationManager.builder();
        // 위에서부터 처음 일치한 규칙을 적용한다.
        messages
                // DM은 경로에 포함된 두 사용자 중 본인만 구독할 수 있다.
                .simpSubscribeDestMatchers(DM_SUBSCRIPTION_PREFIX + "*")
                .access((authentication, context) -> new AuthorizationDecision(
                        isConversationParticipant(authentication.get(), context.getMessage())
                ))
                // 다른 경로의 구독은 차단한다.
                .simpTypeMatchers(SimpMessageType.SUBSCRIBE).denyAll()
                // 로그인한 사용자만 지정된 채팅 전송 경로를 사용할 수 있다.
                .simpDestMatchers("/pub/direct-messages_send").authenticated()
                // SEND는 내부적으로 MESSAGE 타입이다. /sub 직접 전송 등 나머지 전송은 차단한다.
                .simpTypeMatchers(SimpMessageType.MESSAGE).denyAll()
                // 연결·구독 취소·연결 종료·하트비트에도 인증이 필요하다.
                .simpTypeMatchers(
                        SimpMessageType.CONNECT,
                        SimpMessageType.UNSUBSCRIBE,
                        SimpMessageType.DISCONNECT,
                        SimpMessageType.HEARTBEAT
                ).authenticated()
                // 명시적으로 허용하지 않은 메시지는 모두 차단한다.
                .anyMessage().denyAll();
        return messages.build();
    }

    private boolean isConversationParticipant(Authentication authentication, Message<?> message) {
        // JWT 인증으로 설정된 사용자 UUID가 있어야 한다.
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UUID userId)) {
            return false;
        }

        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination == null || !destination.startsWith(DM_SUBSCRIPTION_PREFIX)) {
            return false;
        }

        // /sub/direct-messages_{UUID1}_{UUID2}에서 두 사용자 ID를 꺼낸다. DB 조회는 하지 않는다.
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
                    // 로그인한 사용자가 두 참여자 중 하나인지 확인한다.
                    && (userId.equals(first) || userId.equals(second));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
