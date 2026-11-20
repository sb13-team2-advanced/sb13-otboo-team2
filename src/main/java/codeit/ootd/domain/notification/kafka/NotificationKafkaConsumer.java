package codeit.ootd.domain.notification.kafka;

import codeit.ootd.global.realtime.RealtimeMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "otboo.notification.kafka.enabled", havingValue = "true")
public class NotificationKafkaConsumer {
    private final JsonMapper jsonMapper;
    private final NotificationMessageService messageService;
    private final RealtimeMessagePublisher realtime;

    @KafkaListener(topics = "${otboo.notification.kafka.topic:otboo.notification.requested.v1}",
            groupId = "${otboo.notification.kafka.group-id:otboo-notifications-v1}")
    public void consume(String payload) {
        NotificationMessage message = jsonMapper.readValue(payload, NotificationMessage.class);
        // 서비스 트랜잭션이 커밋된 후 브라우저에 전달한다.
        messageService.save(message).ifPresent(realtime::publishNotification);
    }
}
