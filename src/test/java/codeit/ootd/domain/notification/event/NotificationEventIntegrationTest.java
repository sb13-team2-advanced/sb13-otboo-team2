package codeit.ootd.domain.notification.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
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
    @DisplayName("발행 시점에는 저장하지 않고 업무 커밋 후 알림 한 건을 저장한다")
    void savesNotificationAfterCommit() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event());
            assertTrue(receivedNotifications().isEmpty());
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
    }

    @Test
    @DisplayName("업무가 롤백되면 알림을 저장하지 않는다")
    void doesNotSaveOnRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event());
            status.setRollbackOnly();
        });

        assertTrue(receivedNotifications().isEmpty());
    }

    @Test
    @DisplayName("트랜잭션 없이 발행한 이벤트는 처리하지 않는다")
    void ignoresEventWithoutTransaction() {
        eventPublisher.publishEvent(event());

        assertTrue(receivedNotifications().isEmpty());
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
