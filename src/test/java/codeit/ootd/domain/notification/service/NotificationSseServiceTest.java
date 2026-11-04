package codeit.ootd.domain.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.notification.controller.NotificationSseController;
import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import jakarta.servlet.AsyncEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseServiceTest {

  private final NotificationSseService service = new NotificationSseService();

  @Test
  @DisplayName("SSE 응답으로 heartbeat를 수신하고 완료된 연결에는 더 이상 전송하지 않는다")
  void receivesHeartbeatAndStopsSendingToCompletedConnection() throws Exception {
    UUID userId = UUID.randomUUID();
    var mvc = MockMvcBuilders.standaloneSetup(new NotificationSseController(service))
        .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == UUID.class;
          }

          @Override
          public Object resolveArgument(MethodParameter parameter,
              ModelAndViewContainer container, NativeWebRequest request,
              WebDataBinderFactory factory) {
            return userId;
          }
        }).build();

    var result = mvc.perform(get("/api/sse"))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andReturn();
    assertEquals(":connected\n\n", result.getResponse().getContentAsString());
    assertTrue(result.getResponse().getContentType().startsWith("text/event-stream"));

    service.sendHeartbeats();
    assertEquals(":connected\n\n:heartbeat\n\n", result.getResponse().getContentAsString());

    SseEmitter secondTab = service.subscribe(userId);
    assertEquals(2, connections().get(userId).size());

    MockAsyncContext context = (MockAsyncContext) result.getRequest().getAsyncContext();
    for (var listener : context.getListeners()) {
      listener.onComplete(new AsyncEvent(context));
    }
    assertEquals(1, connections().get(userId).size());
    assertTrue(connections().get(userId).containsValue(secondTab));
    service.sendHeartbeats();
    assertEquals(":connected\n\n:heartbeat\n\n", result.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("최초 연결 메시지 전송에 실패하면 등록한 연결을 제거하고 원인 예외를 반환한다")
  void removesConnectionWhenInitialMessageFails() {
    UUID userId = UUID.randomUUID();
    IOException failure = new IOException("disconnected");

    try (var mocked = mockConstruction(SseEmitter.class, (emitter, context) ->
        doThrow(failure).when(emitter).send(any(SseEmitter.SseEventBuilder.class)))) {
      IllegalStateException exception = assertThrows(IllegalStateException.class,
          () -> service.subscribe(userId));

      assertEquals("SSE 연결 확인 메시지 전송에 실패했습니다.", exception.getMessage());
      assertSame(failure, exception.getCause());
      assertFalse(connections().containsKey(userId));
      assertEquals(1, mocked.constructed().size());
    }
  }

  @Test
  @DisplayName("마지막 SSE 연결이 타임아웃되면 사용자 연결 정보를 제거한다")
  void removesUserWhenLastConnectionTimesOut() throws Exception {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = service.subscribe(userId);
    Runnable callback = (Runnable) ReflectionTestUtils.getField(emitter, "timeoutCallback");
    callback.run();
    assertFalse(connections().containsKey(userId));
  }

  @Test
  @DisplayName("사용자 ID가 없으면 SSE 구독을 거부하고 연결을 등록하지 않는다")
  void rejectsMissingUserId() {
    assertThrows(NullPointerException.class, () -> service.subscribe(null));
    assertTrue(connections().isEmpty());
  }

  @Test
  @DisplayName("알림 수신자의 모든 SSE 연결에만 알림 ID와 이벤트명을 포함해 전송한다")
  void sendsNotificationToEveryReceiverConnectionOnly() throws Exception {
    UUID receiverId = UUID.randomUUID();
    SseEmitter firstTab = mock(SseEmitter.class);
    SseEmitter secondTab = mock(SseEmitter.class);
    SseEmitter otherUser = mock(SseEmitter.class);
    connections().put(receiverId, new ConcurrentHashMap<>(Map.of(
        UUID.randomUUID(), firstTab, UUID.randomUUID(), secondTab)));
    connections().put(UUID.randomUUID(), new ConcurrentHashMap<>(Map.of(
        UUID.randomUUID(), otherUser)));
    NotificationDto notification = notification(receiverId);

    service.send(receiverId, notification);

    var event = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
    verify(firstTab).send(event.capture());
    verify(secondTab).send(any(SseEmitter.SseEventBuilder.class));
    verifyNoInteractions(otherUser);
    StringBuilder framing = new StringBuilder();
    boolean containsNotification = false;
    for (var part : event.getValue().build()) {
      if (part.getData() instanceof String text) {
        framing.append(text);
      }
      if (part.getData() == notification) {
        containsNotification = true;
      }
    }
    assertTrue(framing.toString().contains("id:" + notification.id() + "\n"));
    assertTrue(framing.toString().contains("event:notifications\n"));
    assertTrue(containsNotification);
  }

  @Test
  @DisplayName("알림 전송에 실패한 연결을 제거하고 정상 연결에는 계속 전송한다")
  void removesFailedConnectionsAndStillSendsToHealthyConnection() throws Exception {
    UUID receiverId = UUID.randomUUID();
    SseEmitter disconnected = mock(SseEmitter.class);
    SseEmitter completed = mock(SseEmitter.class);
    SseEmitter healthy = mock(SseEmitter.class);
    UUID healthyId = UUID.randomUUID();
    connections().put(receiverId, new ConcurrentHashMap<>(Map.of(
        UUID.randomUUID(), disconnected, UUID.randomUUID(), completed, healthyId, healthy)));
    doThrow(new IOException("disconnected"))
        .when(disconnected).send(any(SseEmitter.SseEventBuilder.class));
    doThrow(new IllegalStateException("completed"))
        .when(completed).send(any(SseEmitter.SseEventBuilder.class));

    service.send(receiverId, notification(receiverId));

    verify(healthy).send(any(SseEmitter.SseEventBuilder.class));
    assertEquals(Map.of(healthyId, healthy), connections().get(receiverId));
  }

  @Test
  @DisplayName("알림 수신자의 SSE 연결이 없으면 전송을 건너뛴다")
  void skipsSendingWhenReceiverIsNotConnected() {
    UUID receiverId = UUID.randomUUID();
    service.send(receiverId, notification(receiverId));
    assertTrue(connections().isEmpty());
  }

  @Test
  @DisplayName("heartbeat 실패 연결을 제거하고 다른 사용자의 정상 연결에는 계속 전송한다")
  void removesFailedHeartbeatConnectionsAndKeepsSending() throws Exception {
    UUID receiverId = UUID.randomUUID();
    SseEmitter disconnected = mock(SseEmitter.class);
    SseEmitter completed = mock(SseEmitter.class);
    SseEmitter healthy = mock(SseEmitter.class);
    connections().put(receiverId, new ConcurrentHashMap<>(Map.of(
        UUID.randomUUID(), disconnected, UUID.randomUUID(), completed)));
    connections().put(UUID.randomUUID(), new ConcurrentHashMap<>(Map.of(
        UUID.randomUUID(), healthy)));
    doThrow(new IOException("disconnected"))
        .when(disconnected).send(any(SseEmitter.SseEventBuilder.class));
    doThrow(new IllegalStateException("completed"))
        .when(completed).send(any(SseEmitter.SseEventBuilder.class));

    service.sendHeartbeats();

    assertFalse(connections().containsKey(receiverId));
    verify(healthy).send(any(SseEmitter.SseEventBuilder.class));
    service.sendHeartbeats();
    verify(disconnected).send(any(SseEmitter.SseEventBuilder.class));
    verify(completed).send(any(SseEmitter.SseEventBuilder.class));
  }

  private NotificationDto notification(UUID receiverId) {
    return new NotificationDto(UUID.randomUUID(), Instant.now(), receiverId,
        "새 알림", "알림 내용", NotificationLevel.INFO);
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, Map<UUID, SseEmitter>> connections() {
    return (Map<UUID, Map<UUID, SseEmitter>>) ReflectionTestUtils.getField(service, "emitters");
  }
}
