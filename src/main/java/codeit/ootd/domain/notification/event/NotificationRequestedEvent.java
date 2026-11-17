package codeit.ootd.domain.notification.event;

import codeit.ootd.domain.notification.enums.NotificationLevel;
import java.util.Objects;
import java.util.UUID;

/**
 * 업무 트랜잭션 안에서 발행하는 알림 생성 요청. 성공적으로 커밋된 경우에만 저장한다.
 */
public record NotificationRequestedEvent(
        UUID receiverId,
        String title,
        String content,
        NotificationLevel level
) {
    public NotificationRequestedEvent {
        Objects.requireNonNull(receiverId, "수신자 ID는 null일 수 없습니다.");
        Objects.requireNonNull(title, "알림 제목은 null일 수 없습니다.");
        Objects.requireNonNull(content, "알림 내용은 null일 수 없습니다.");
        Objects.requireNonNull(level, "알림 수준은 null일 수 없습니다.");
    }
}
