package codeit.ootd.domain.directmessage.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.directmessage.exception.DirectMessageUserNotFoundException;
import codeit.ootd.domain.directmessage.service.DirectMessageService;
import codeit.ootd.global.config.WebSocketConfig;
import codeit.ootd.global.security.AuthSessionWebSocketRegistry;
import codeit.ootd.global.security.StompJwtAuthenticationInterceptor;
import codeit.ootd.global.security.WebSocketSecurityConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig({WebSocketConfig.class, WebSocketSecurityConfig.class,
        DirectMessageStompController.class, DirectMessageStompControllerTest.MvcConfig.class})
class DirectMessageStompControllerTest {

    @Configuration
    @EnableWebMvc
    static class MvcConfig {
        // 실제 앱처럼 WebSocket에서도 MVC의 검증기를 사용한다.
    }

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RECEIVER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String SESSION = "dm-error-request";
    private final BlockingQueue<Message<?>> errors = new LinkedBlockingQueue<>();

    @Autowired @Qualifier("clientInboundChannel") private MessageChannel inbound;
    @Autowired @Qualifier("brokerChannel") private AbstractMessageChannel broker;
    @MockitoBean private DirectMessageService service;
    @MockitoBean private StompJwtAuthenticationInterceptor authenticationInterceptor;
    @MockitoBean private AuthSessionWebSocketRegistry sessions;

    private final ChannelInterceptor errorCollector = new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
            if (destination != null && destination.equals("/user/" + USER + "/sub/direct-messages_errors")) {
                errors.add(message);
            }
            return message;
        }
    };

    @BeforeEach
    void setUp() {
        when(authenticationInterceptor.preSend(any(), any())).thenAnswer(call -> call.getArgument(0));
        broker.addInterceptor(errorCollector);
    }

    @AfterEach
    void tearDown() {
        broker.removeInterceptor(errorCollector);
    }

    @Test
    @DisplayName("업무 오류 응답에는 요청 사용자와 세션을 지정한다")
    void businessErrorOnlyGoesToRequestSession() throws Exception {
        doThrow(new DirectMessageUserNotFoundException()).when(service).send(any(), eq(USER));

        send(request("hello"));

        JsonNode error = receiveError();
        assertThat(error.get("exceptionName").asText()).isEqualTo("DirectMessageUserNotFoundException");
        assertThat(error.get("message").asText()).isEqualTo("DM 사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("빈 내용은 서비스 호출 전에 검증하고 필드 오류를 요청 세션에 응답한다")
    void validationFailureIncludesFieldDetails() throws Exception {
        send(request("   "));

        JsonNode error = receiveError();
        assertThat(error.get("exceptionName").asText()).isEqualTo("MethodArgumentNotValidException");
        assertThat(error.get("details").has("content")).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("JSON 변환 실패는 서비스 호출 없이 오류로 응답한다")
    void malformedPayloadReturnsError() throws Exception {
        send("{broken-json");

        JsonNode error = receiveError();
        assertThat(error.get("exceptionName").asText()).isEqualTo("MessageConversionException");
        assertThat(error.get("message").asText()).isEqualTo("요청 값이 올바르지 않습니다.");
        verifyNoInteractions(service);
    }

    private void send(String payload) {
        var headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setSessionId(SESSION);
        headers.setSessionAttributes(new HashMap<>());
        headers.setUser(authentication(USER));
        headers.setDestination("/pub/direct-messages_send");
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        inbound.send(MessageBuilder.createMessage(payload.getBytes(StandardCharsets.UTF_8),
                headers.getMessageHeaders()));
    }

    private JsonNode receiveError() throws Exception {
        Message<?> message = errors.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(SimpMessageHeaderAccessor.getDestination(message.getHeaders()))
                .isEqualTo("/user/" + USER + "/sub/direct-messages_errors");
        assertThat(SimpMessageHeaderAccessor.getSessionId(message.getHeaders())).isEqualTo(SESSION);
        return JsonMapper.builder().build().readTree((byte[]) message.getPayload());
    }

    private UsernamePasswordAuthenticationToken authentication(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private String request(String content) {
        return "{\"senderId\":\"" + USER + "\",\"receiverId\":\"" + RECEIVER
                + "\",\"content\":\"" + content + "\"}";
    }
}
