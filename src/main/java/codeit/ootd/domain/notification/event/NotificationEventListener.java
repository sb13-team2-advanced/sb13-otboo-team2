package codeit.ootd.domain.notification.event;

import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.service.NotificationService;
import codeit.ootd.domain.notification.service.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationRequestedEvent event) {
        NotificationDto notification = notificationService.saveNotification(
                event.receiverId(), event.title(), event.content(), event.level());
        notificationSseService.send(notification.receiverId(), notification);
    }
}
