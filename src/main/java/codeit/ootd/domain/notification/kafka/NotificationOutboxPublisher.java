package codeit.ootd.domain.notification.kafka;

import codeit.ootd.domain.notification.entity.NotificationOutbox;
import codeit.ootd.domain.notification.repository.NotificationOutboxRepository;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "otboo.notification.kafka.enabled", havingValue = "true")
public class NotificationOutboxPublisher {
    private final NotificationOutboxRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${otboo.notification.kafka.topic:otboo.notification.requested.v1}")
    private String topic;

    @Scheduled(fixedDelayString = "${otboo.notification.kafka.publish-delay-ms:1000}",
            scheduler = "notificationKafkaScheduler")
    public void publish() {
        for (NotificationOutbox entry : outbox.findTop100ByOrderByCreatedAtAscIdAsc()) {
            try {
                kafkaTemplate.send(topic, entry.getMessageKey(), entry.getPayload()).get(10, TimeUnit.SECONDS);
                outbox.deleteById(entry.getId());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                // 성공 확인 전에는 삭제하지 않는다. 재발행 중복은 소비자의 처리 이력으로 막는다.
                log.warn("알림 Kafka 발행에 실패했습니다. 다음 주기에 재시도합니다. 발송 대기 ID={}", entry.getId(), exception);
                return;
            }
        }
    }
}
