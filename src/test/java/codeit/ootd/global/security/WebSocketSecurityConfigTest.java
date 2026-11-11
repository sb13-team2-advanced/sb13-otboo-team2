package codeit.ootd.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import codeit.ootd.global.config.WebSocketConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;

@SpringJUnitWebConfig({WebSocketConfig.class, WebSocketSecurityConfig.class})
class WebSocketSecurityConfigTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String ROOM = "/sub/direct-messages_" + FIRST + "_" + SECOND;

    @Autowired
    private AuthorizationManager<Message<?>> manager;

    @Autowired
    @Qualifier("clientInboundChannel")
    private MessageChannel inboundChannel;

    @MockitoBean
    private StompJwtAuthenticationInterceptor authenticationInterceptor;

    @BeforeEach
    void stubAuthentication() {
        // JWT 검증은 별도 단위 테스트에서 확인하고, 여기서는 인증 이후의 채널 설정을 검증한다.
        when(authenticationInterceptor.preSend(any(), any())).thenAnswer(invocation -> {
            Message<?> message = invocation.getArgument(0);
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor.getCommand() == StompCommand.CONNECT) {
                accessor.setUser(user(FIRST));
            }
            return message;
        });
    }

    @Test
    @DisplayName("인증된 CONNECT는 CSRF 토큰 없이 인바운드 채널을 통과한다")
    void connect_withoutCsrf_authenticates() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("csrf-free-connect-test");
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);

        assertThat(inboundChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders())))
                .isTrue();
        assertThat(accessor.getUser().getName()).isEqualTo(FIRST.toString());
    }

    @Test
    @DisplayName("실제 인바운드 채널은 타인의 DM 구독을 계속 차단한다")
    void inboundChannel_rejectsThirdPartySubscription() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("unauthorized-subscription-test");
        accessor.setSubscriptionId("subscription-test");
        accessor.setUser(user(UUID.randomUUID()));
        accessor.setDestination(ROOM);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> inboundChannel.send(message))
                .isInstanceOf(MessageDeliveryException.class)
                .hasCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("DM의 두 참여자는 해당 대화를 구독할 수 있다")
    void eitherParticipant_canSubscribe() {
        assertThat(allowed(user(FIRST), StompCommand.SUBSCRIBE, ROOM)).isTrue();
        assertThat(allowed(user(SECOND), StompCommand.SUBSCRIBE, ROOM)).isTrue();
    }

    @Test
    @DisplayName("대화에 참여하지 않은 사용자는 DM을 구독할 수 없다")
    void thirdParty_cannotSubscribe() {
        assertThat(allowed(user(UUID.randomUUID()), StompCommand.SUBSCRIBE, ROOM)).isFalse();
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 DM을 구독하거나 전송할 수 없다")
    void anonymous_cannotSubscribeOrSend() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(allowed(anonymous, StompCommand.SUBSCRIBE, ROOM)).isFalse();
        assertThat(allowed(anonymous, StompCommand.SEND, "/pub/direct-messages_send")).isFalse();
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @DisplayName("잘못된 DM 경로와 허용하지 않은 경로는 구독할 수 없다")
    @ValueSource(strings = {
            "/sub/direct-messages_invalid_invalid", "/sub/other"
    })
    void malformedOrOtherSubscription_isDenied(String destination) {
        assertThat(allowed(user(FIRST), StompCommand.SUBSCRIBE, destination)).isFalse();
    }

    @Test
    @DisplayName("지정한 전송 경로만 허용하고 브로커 직접 전송은 차단한다")
    void send_onlyAllowsApplicationDestination() {
        assertThat(allowed(user(FIRST), StompCommand.SEND, "/pub/direct-messages_send")).isTrue();
        assertThat(allowed(user(FIRST), StompCommand.SEND, ROOM)).isFalse();
        assertThat(allowed(user(FIRST), StompCommand.SEND, "/pub/other")).isFalse();
        assertThat(allowed(user(FIRST), StompCommand.SEND, null)).isFalse();
    }

    private Authentication user(UUID id) {
        return new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private boolean allowed(Authentication user, StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return manager.authorize(() -> user, message).isGranted();
    }
}
