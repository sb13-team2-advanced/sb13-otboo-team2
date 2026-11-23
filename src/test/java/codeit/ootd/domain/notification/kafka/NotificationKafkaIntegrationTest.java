package codeit.ootd.domain.notification.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.domain.notification.repository.NotificationOutboxRepository;
import codeit.ootd.domain.notification.repository.NotificationReceiptRepository;
import codeit.ootd.domain.notification.service.NotificationSseService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.config.JpaAuditingConfig;
import codeit.ootd.global.config.KafkaConfig;
import codeit.ootd.global.realtime.LocalRealtimeMessagePublisher;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(properties = {
        "otboo.kafka.enabled=true",
        "otboo.notification.kafka.enabled=true",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.enable-auto-commit=false",
        "spring.kafka.listener.ack-mode=record"
})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EmbeddedKafka(partitions = 1, topics = {"otboo.notification.requested.v1", "otboo.notification.requested.v1.DLT"})
@ImportAutoConfiguration(KafkaAutoConfiguration.class)
@Import({LocalRealtimeMessagePublisher.class, JpaAuditingConfig.class, KafkaConfig.class, NotificationKafkaConfig.class, NotificationOutboxListener.class,
        NotificationOutboxPublisher.class, NotificationKafkaConsumer.class, NotificationMessageService.class,
        NotificationKafkaIntegrationTest.JsonConfig.class})
@DisplayName("Kafka 알림 처리 통합 테스트")
class NotificationKafkaIntegrationTest {
    @Autowired ApplicationEventPublisher events;
    @Autowired NotificationOutboxRepository outbox;
    @Autowired NotificationReceiptRepository receipts;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired NotificationOutboxPublisher publisher;
    @Autowired NotificationMessageService messageService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @MockitoBean NotificationSseService sse;
    @MockitoBean SimpMessagingTemplate messagingTemplate;

    private TransactionTemplate transaction;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        receiverId = users.saveAndFlush(User.create("수신자", UUID.randomUUID() + "@example.com", "encoded")).getId();
    }

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        notifications.deleteAll();
        receipts.deleteAll();
        users.deleteById(receiverId);
    }

    @Test
    @DisplayName("커밋된 알림 요청을 Kafka로 전달하여 알림을 저장하고 SSE로 전송한다")
    void committedRequestTravelsThroughKafkaAndCreatesNotification() {
        transaction.executeWithoutResult(status -> events.publishEvent(event()));
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(notifications.count()).isZero();
        verifyNoInteractions(sse);

        publisher.publish();

        verify(sse, timeout(15000)).send(eq(receiverId), any());
        assertThat(outbox.count()).isZero();
        assertThat(receipts.count()).isEqualTo(1);
        var saved = notifications.findAll().get(0);
        assertThat(saved.getContent()).isEqualTo("알림 내용");
        assertThat(saved.getReceiverId()).isEqualTo(receiverId);
    }

    @Test
    @DisplayName("롤백되거나 트랜잭션 없이 발행한 알림 요청은 발송 대기함에 저장하지 않는다")
    void rollbackAndNonTransactionalEventsDoNotLeaveOutboxEntries() {
        transaction.executeWithoutResult(status -> {
            events.publishEvent(event());
            status.setRollbackOnly();
        });
        events.publishEvent(event());
        assertThat(outbox.count()).isZero();
        assertThat(notifications.count()).isZero();
        verifyNoInteractions(sse);
    }

    @Test
    @DisplayName("읽음 처리로 삭제한 알림은 같은 이벤트를 다시 받아도 재생성하지 않는다")
    void duplicateEventDoesNotRecreateReadNotification() {
        var message = new NotificationMessage(UUID.randomUUID(), event());
        var saved = messageService.save(message).orElseThrow();
        notifications.deleteById(saved.id());

        assertThat(messageService.save(message)).isEmpty();
        assertThat(notifications.count()).isZero();
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("알림 저장에 실패하면 재처리할 수 있도록 처리 이력도 롤백한다")
    void failedNotificationSaveRollsBackReceiptSoItCanBeRetried() {
        var message = new NotificationMessage(UUID.randomUUID(), new NotificationRequestedEvent(
                receiverId, "가".repeat(256), "내용", NotificationLevel.INFO));
        assertThatThrownBy(() -> messageService.save(message)).isInstanceOf(RuntimeException.class);
        assertThat(receipts.existsById(message.eventId())).isFalse();
        assertThat(notifications.count()).isZero();
    }

    @Test
    @DisplayName("잘못된 형식의 메시지는 실패 메시지 보관 토픽에 원문과 오류 정보를 남긴다")
    void malformedMessageIsPreservedInDeadLetterTopic() throws Exception {
        var props = KafkaTestUtils.consumerProps("notification-dlt-test", "false", broker);
        try (var consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "otboo.notification.requested.v1.DLT");
            kafka.send("otboo.notification.requested.v1", "잘못된 메시지", "올바른 JSON 형식이 아닙니다.").get();
            var record = KafkaTestUtils.getSingleRecord(consumer,
                    "otboo.notification.requested.v1.DLT", Duration.ofSeconds(15));
            assertThat(record.value()).isEqualTo("올바른 JSON 형식이 아닙니다.");
            assertThat(record.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
        }
        assertThat(notifications.count()).isZero();
    }

    private NotificationRequestedEvent event() {
        return new NotificationRequestedEvent(receiverId, "새 알림", "알림 내용", NotificationLevel.INFO);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JsonConfig {
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}
