package codeit.ootd.domain.notification.service;

import codeit.ootd.domain.notification.dto.response.NotificationDto;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationSseService {

  private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

  // 사용자 ID → 연결 ID → 연결
  private final Map<UUID, Map<UUID, SseEmitter>> emitters = new ConcurrentHashMap<>();

  public SseEmitter subscribe(UUID userId) {
    Objects.requireNonNull(userId, "사용자 ID는 null일 수 없습니다.");
    UUID connectionId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);

    emitter.onCompletion(() -> remove(userId, connectionId));
    emitter.onTimeout(() -> {
      remove(userId, connectionId);
      emitter.complete();
    });
    emitter.onError(error -> remove(userId, connectionId));

    emitters.compute(userId, (id, connections) -> {
      if (connections == null) {
        connections = new ConcurrentHashMap<>();
      }
      connections.put(connectionId, emitter);
      return connections;
    });

    try {
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (IOException | IllegalStateException e) {
      remove(userId, connectionId);
      throw new IllegalStateException("SSE 연결 확인 메시지 전송에 실패했습니다.", e);
    }
    return emitter;
  }

  // 프런트 EventSourcePolyfill의 기본 수신 대기 시간(45초)보다 짧게 유지한다.
  @Scheduled(fixedDelay = 30_000, scheduler = "notificationHeartbeatScheduler")
  public void sendHeartbeats() {
    emitters.forEach((userId, connections) ->
        connections.forEach((connectionId, emitter) -> {
          try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
          } catch (IOException | IllegalStateException e) {
            remove(userId, connectionId);
          }
        }));
  }

  public void send(UUID receiverId, NotificationDto notification) {
    Objects.requireNonNull(receiverId, "수신자 ID는 null일 수 없습니다.");
    Objects.requireNonNull(notification, "알림은 null일 수 없습니다.");

    emitters.getOrDefault(receiverId, Map.of()).forEach((connectionId, emitter) -> {
      try {
        emitter.send(SseEmitter.event()
            .id(notification.id().toString())
            .name("notifications")
            .data(notification));
      } catch (IOException | IllegalStateException e) {
        remove(receiverId, connectionId);
      }
    });
  }

  private void remove(UUID userId, UUID connectionId) {
    emitters.computeIfPresent(userId, (id, connections) -> {
      connections.remove(connectionId);
      return connections.isEmpty() ? null : connections;
    });
  }
}
