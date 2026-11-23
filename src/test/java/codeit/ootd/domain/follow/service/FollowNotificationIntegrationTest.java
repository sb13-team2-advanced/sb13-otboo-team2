package codeit.ootd.domain.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.follow.dto.request.FollowCreateRequest;
import codeit.ootd.domain.follow.exception.FollowAlreadyExistsException;
import codeit.ootd.domain.follow.repository.FollowQueryRepository;
import codeit.ootd.domain.follow.repository.FollowRepository;
import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.event.NotificationEventListener;
import codeit.ootd.global.realtime.LocalRealtimeMessagePublisher;
import codeit.ootd.domain.notification.repository.NotificationQueryRepository;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.domain.notification.service.NotificationService;
import codeit.ootd.domain.notification.service.NotificationSseService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.config.JpaAuditingConfig;
import codeit.ootd.global.config.QuerydslConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({LocalRealtimeMessagePublisher.class, FollowService.class, FollowQueryRepository.class, NotificationEventListener.class,
        NotificationService.class, NotificationQueryRepository.class,
        QuerydslConfig.class, JpaAuditingConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
class FollowNotificationIntegrationTest {

    @Autowired private FollowService service;
    @Autowired private FollowRepository follows;
    @Autowired private NotificationRepository notifications;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private NotificationSseService sse;
    @MockitoBean private SimpMessagingTemplate messagingTemplate;

    private User follower;
    private User followee;

    @BeforeEach
    void setUp() {
        follower = users.saveAndFlush(User.create("팔로워", UUID.randomUUID() + "@example.com", "encoded"));
        followee = users.saveAndFlush(User.create("팔로우 대상", UUID.randomUUID() + "@example.com", "encoded"));
    }

    @AfterEach
    void tearDown() {
        notifications.deleteAll(receivedNotifications());
        follows.deleteAll(follows.findAll().stream()
                .filter(follow -> follow.getFollowerId().equals(follower.getId())
                        || follow.getFollowerId().equals(followee.getId())).toList());
        users.deleteAllById(List.of(follower.getId(), followee.getId()));
    }

    @Test
    @DisplayName("팔로우 커밋 후 대상 사용자에게만 알림을 저장하고 SSE로 전달한다")
    void notifiesFolloweeAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.create(request(), follower.getId());
            assertThat(receivedNotifications()).isEmpty();
            verifyNoInteractions(sse);
        });

        assertThat(receivedNotifications()).hasSize(1);
        Notification saved = receivedNotifications().get(0);
        assertThat(saved.getReceiverId()).isEqualTo(followee.getId());
        assertThat(saved.getTitle()).isEqualTo("새 팔로워가 생겼습니다.");
        assertThat(saved.getContent()).isEqualTo(follower.getName() + "님이 회원님을 팔로우했습니다.");
        assertThat(saved.getLevel()).isEqualTo(NotificationLevel.INFO);
        var payload = ArgumentCaptor.forClass(NotificationDto.class);
        verify(sse).send(eq(followee.getId()), payload.capture());
        assertThat(payload.getValue().id()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("팔로우가 롤백되면 관계와 알림을 저장하거나 전송하지 않는다")
    void rollbackDoesNotNotify() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.create(request(), follower.getId());
            status.setRollbackOnly();
        });

        assertThat(follows.existsByFollowerIdAndFolloweeId(follower.getId(), followee.getId())).isFalse();
        assertThat(receivedNotifications()).isEmpty();
        verifyNoInteractions(sse);
    }

    @Test
    @DisplayName("중복 팔로우 요청으로 추가 알림이 생성되지 않는다")
    void duplicateDoesNotNotifyAgain() {
        service.create(request(), follower.getId());
        assertThatThrownBy(() -> service.create(request(), follower.getId()))
                .isInstanceOf(FollowAlreadyExistsException.class);

        assertThat(receivedNotifications()).hasSize(1);
        verify(sse).send(eq(followee.getId()), any(NotificationDto.class));
    }

    private FollowCreateRequest request() {
        return new FollowCreateRequest(followee.getId(), follower.getId());
    }

    private List<Notification> receivedNotifications() {
        return notifications.findAll().stream()
                .filter(notification -> notification.getReceiverId().equals(follower.getId())
                        || notification.getReceiverId().equals(followee.getId())).toList();
    }
}
