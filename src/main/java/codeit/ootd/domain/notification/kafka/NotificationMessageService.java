package codeit.ootd.domain.notification.kafka;

import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.entity.NotificationReceipt;
import codeit.ootd.domain.notification.repository.NotificationReceiptRepository;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "otboo.notification.kafka.enabled", havingValue = "true")
public class NotificationMessageService {
    private final NotificationReceiptRepository receipts;
    private final NotificationRepository notifications;

    @Transactional
    public Optional<NotificationDto> save(NotificationMessage message) {
        if (receipts.existsById(message.eventId())) {
            return Optional.empty();
        }
        // 동시 수신은 PK로 차단한다. 충돌한 트랜잭션은 롤백 후 Kafka가 재시도한다.
        receipts.saveAndFlush(new NotificationReceipt(message.eventId()));
        var event = message.notification();
        Notification saved = notifications.save(new Notification(
                event.receiverId(), event.title(), event.content(), event.level()));
        return Optional.of(new NotificationDto(saved.getId(), saved.getCreatedAt(), saved.getReceiverId(),
                saved.getTitle(), saved.getContent(), saved.getLevel()));
    }
}
