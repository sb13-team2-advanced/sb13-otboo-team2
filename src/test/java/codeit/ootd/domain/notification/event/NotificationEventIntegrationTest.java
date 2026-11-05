package codeit.ootd.domain.notification.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.domain.notification.service.NotificationService;
import codeit.ootd.domain.notification.service.NotificationSseService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class NotificationEventIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        receiverId = userRepository.saveAndFlush(User.create(
                "알림 수신자", UUID.randomUUID() + "@example.com", "encoded-password")).getId();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll(receivedNotifications());
        userRepository.deleteById(receiverId);
    }

    @Test
    @DisplayName("업무 커밋 후 알림을 저장하고 저장된 DTO를 SSE로 전송한다")
    void savesNotificationAfterCommit() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event());
            assertTrue(receivedNotifications().isEmpty());
            verifyNoInteractions(notificationSseService);
        });

        List<Notification> notifications = receivedNotifications();
        assertEquals(1, notifications.size());
        Notification saved = notifications.get(0);
        assertEquals(receiverId, saved.getReceiverId());
        assertEquals("새 알림", saved.getTitle());
        assertEquals("알림 내용", saved.getContent());
        assertEquals(NotificationLevel.INFO, saved.getLevel());
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        var notification = ArgumentCaptor.forClass(NotificationDto.class);
        verify(notificationSseService).send(eq(receiverId), notification.capture());
        NotificationDto sent = notification.getValue();
        assertEquals(saved.getId(), sent.id());
        assertNotNull(sent.createdAt());
        assertEquals(saved.getReceiverId(), sent.receiverId());
        assertEquals(saved.getTitle(), sent.title());
        assertEquals(saved.getContent(), sent.content());
        assertEquals(saved.getLevel(), sent.level());
    }

    @Test
    @DisplayName("업무가 롤백되면 알림 저장과 SSE 전송을 하지 않는다")
    void doesNotSaveOnRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event());
            status.setRollbackOnly();
        });

        assertTrue(receivedNotifications().isEmpty());
        verifyNoInteractions(notificationSseService);
    }

    @Test
    @DisplayName("트랜잭션 없이 발행한 이벤트는 처리하지 않는다")
    void ignoresEventWithoutTransaction() {
        eventPublisher.publishEvent(event());

        assertTrue(receivedNotifications().isEmpty());
        verifyNoInteractions(notificationSseService);
    }

    @Test
    @DisplayName("알림 저장 후 생성된 ID와 생성 시간을 포함한 DTO를 반환한다")
    void returnsSavedNotificationDto() {
        var result = notificationService.saveNotification(
                receiverId, "새 알림", "알림 내용", NotificationLevel.INFO);

        assertNotNull(result.id());
        assertNotNull(result.createdAt());
        Notification saved = notificationRepository.findById(result.id()).orElseThrow();
        // DB는 생성 시간을 마이크로초 정밀도로 저장한다.
        assertTrue(Duration.between(saved.getCreatedAt(), result.createdAt())
                .abs().toNanos() < 1_000);
        assertEquals(saved.getReceiverId(), result.receiverId());
        assertEquals(saved.getTitle(), result.title());
        assertEquals(saved.getContent(), result.content());
        assertEquals(saved.getLevel(), result.level());
    }

    private NotificationRequestedEvent event() {
        return new NotificationRequestedEvent(
                receiverId, "새 알림", "알림 내용", NotificationLevel.INFO);
    }

    private List<Notification> receivedNotifications() {
        return notificationRepository.findAll().stream()
                .filter(notification -> notification.getReceiverId().equals(receiverId))
                .toList();
    }
}
