package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedCommentService;
import codeit.ootd.domain.feed.service.FeedLikeService;
import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.domain.notification.service.NotificationSseService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-notification-integration;DB_CLOSE_DELAY=-1"
})
@DisplayName("피드 알림 연동 통합 테스트")
class FeedNotificationIntegrationTest {

    @Autowired
    private FeedLikeService feedLikeService;

    @Autowired
    private FeedCommentService feedCommentService;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedLikeRepository feedLikeRepository;

    @Autowired
    private FeedCommentRepository feedCommentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private TransactionTemplate transactionTemplate;
    private UUID feedId;
    private UUID feedAuthorId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        feedAuthorId = createUser("피드 작성자");
        actorId = createUser("반응한 사용자");

        Feed feed = feedRepository.saveAndFlush(
                Feed.builder()
                        .authorId(feedAuthorId)
                        .weatherId(UUID.randomUUID())
                        .content("오늘의 코디")
                        .weatherSkyStatus("CLEAR")
                        .weatherPrecipitationType("NONE")
                        .build()
        );
        feedId = feed.getId();
    }

    @AfterEach
    void cleanUp() {
        // 이 테스트 클래스 전용 H2 DB의 데이터를 정리한다.
        notificationRepository.deleteAll();
        feedCommentRepository.deleteAll();
        feedLikeRepository.deleteAll();
        feedRepository.deleteAll();
        userRepository.deleteAll();
    }

    @ParameterizedTest(name = "좋아요 작업 여부: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("좋아요·댓글 커밋 후 피드 작성자의 알림을 저장하고 전송한다")
    void savesNotificationAfterCommit(boolean like) {
        transactionTemplate.executeWithoutResult(status -> {
            performAction(like);

            assertThat(notificationRepository.findAll()).isEmpty();
            verifyNoInteractions(notificationSseService);
        });

        assertThat(notificationRepository.findAll())
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getReceiverId())
                            .isEqualTo(feedAuthorId);
                    assertThat(notification.getTitle()).isNotBlank();
                    assertThat(notification.getContent()).isNotBlank();
                    assertThat(notification.getLevel())
                            .isEqualTo(NotificationLevel.INFO);
                });

        Notification saved = notificationRepository.findAll().get(0);
        ArgumentCaptor<NotificationDto> captor =
                ArgumentCaptor.forClass(NotificationDto.class);

        verify(notificationSseService)
                .send(eq(feedAuthorId), captor.capture());

        assertThat(captor.getValue().id()).isEqualTo(saved.getId());
        assertThat(captor.getValue().receiverId()).isEqualTo(feedAuthorId());

        Feed updatedFeed = feedRepository.findById(feedId).orElseThrow();
        assertThat(updatedFeed.getLikeCount()).isEqualTo(like ? 1L : 0L);
        assertThat(updatedFeed.getCommentCount()).isEqualTo(like ? 0 : 1);
        assertThat(feedLikeRepository.count()).isEqualTo(like ? 1L : 0L);
        assertThat(feedCommentRepository.count()).isEqualTo(like ? 0L : 1L);
    }

    @ParameterizedTest(name = "좋아요 작업 여부: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("좋아요·댓글 작업이 롤백되면 알림을 저장하거나 전송하지 않는다")
    void doesNotNotifyAfterRollback(boolean like) {
        transactionTemplate.executeWithoutResult(status -> {
            performAction(like);
            status.setRollbackOnly();
        });

        assertThat(notificationRepository.findAll()).isEmpty();
        verifyNoInteractions(notificationSseService);

        assertThat(feedLikeRepository.count()).isZero();
        assertThat(feedCommentRepository.count()).isZero();

        Feed unchangedFeed = feedRepository.findById(feedId).orElseThrow();
        assertThat(unchangedFeed.getLikeCount()).isZero();
        assertThat(unchangedFeed.getCommentCount()).isZero();
    }

    private void performAction(boolean like) {
        if (like) {
            feedLikeService.like(feedId, actorId);
            return;
        }

        feedCommentService.create(
                feedId,
                new CommentCreateRequest(feedId, actorId, "잘 어울려요!"),
                actorId
        );
    }

    private UUID feedAuthorId() {
        return feedAuthorId;
    }

    private UUID createUser(String name) {
        return userRepository.saveAndFlush(
                User.create(
                        name,
                        UUID.randomUUID() + "@feed-notification.test",
                        "encoded-password"
                )
        ).getId();
    }
}