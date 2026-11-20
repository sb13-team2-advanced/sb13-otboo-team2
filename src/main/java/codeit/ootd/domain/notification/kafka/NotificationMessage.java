package codeit.ootd.domain.notification.kafka;

import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
import java.util.Objects;
import java.util.UUID;

/** Kafka 계약. HTTP 응답 DTO와 독립적으로 유지한다. */
public record NotificationMessage(UUID eventId, NotificationRequestedEvent notification) {
    public NotificationMessage {
        Objects.requireNonNull(eventId, "이벤트 ID는 null일 수 없습니다.");
        Objects.requireNonNull(notification, "알림 요청은 null일 수 없습니다.");
    }
}
