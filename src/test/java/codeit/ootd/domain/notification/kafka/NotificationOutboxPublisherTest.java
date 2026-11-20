package codeit.ootd.domain.notification.kafka;

import codeit.ootd.domain.notification.entity.NotificationOutbox;
import codeit.ootd.domain.notification.repository.NotificationOutboxRepository;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 발송 대기함 Kafka 발행 테스트")
class NotificationOutboxPublisherTest {
    @Mock NotificationOutboxRepository outbox;
    @Mock KafkaTemplate<String, String> kafka;
    @InjectMocks NotificationOutboxPublisher publisher;

    @Test
    @DisplayName("Kafka 전송에 실패하면 다음 시도를 위해 발송 대기 요청을 삭제하지 않는다")
    void failedSendKeepsOutboxForNextAttempt() {
        var entry = new NotificationOutbox("알림 내용", "수신자");
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(publisher, "topic", "notifications");
        when(outbox.findTop100ByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(entry));
        when(kafka.send("notifications", "수신자", "알림 내용"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka에 연결할 수 없습니다.")));

        publisher.publish();

        verify(outbox, never()).deleteById(any());
    }
}
