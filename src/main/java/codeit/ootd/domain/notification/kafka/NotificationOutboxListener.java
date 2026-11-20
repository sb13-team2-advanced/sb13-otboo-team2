package codeit.ootd.domain.notification.kafka;

import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
import codeit.ootd.domain.notification.entity.NotificationOutbox;
import codeit.ootd.domain.notification.repository.NotificationOutboxRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "otboo.notification.kafka.enabled", havingValue = "true")
public class NotificationOutboxListener {
    private final NotificationOutboxRepository outbox;
    private final JsonMapper jsonMapper;

    // 업무와 같은 트랜잭션에 기록하므로 롤백되거나 서버가 종료되어도 발행 요청을 잃지 않는다.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(NotificationRequestedEvent event) {
        String payload = jsonMapper.writeValueAsString(new NotificationMessage(UUID.randomUUID(), event));
        outbox.save(new NotificationOutbox(payload, event.receiverId().toString()));
    }
}
