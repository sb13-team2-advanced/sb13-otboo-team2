package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.domain.follow.entity.Follow;
import codeit.ootd.domain.follow.repository.FollowRepository;
import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.domain.notification.service.NotificationSseService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-follow-notification-integration;DB_CLOSE_DELAY=-1"
})
@DisplayName("새 피드 팔로워 알림 통합 테스트")
class FeedFollowNotificationIntegrationTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedClothesRepository feedClothesRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeatherRepository weatherRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private TransactionTemplate transactionTemplate;
    private UUID authorId;
    private UUID firstFollowerId;
    private UUID secondFollowerId;
    private UUID weatherId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        authorId = createUser("피드 작성자");
        firstFollowerId = createUser("첫 번째 팔로워");
        secondFollowerId = createUser("두 번째 팔로워");
        UUID otherUserId = createUser("작성자가 팔로우하는 사용자");

        followRepository.saveAllAndFlush(List.of(
                new Follow(firstFollowerId, authorId),
                new Follow(secondFollowerId, authorId),
                new Follow(authorId, otherUserId)
        ));

        weatherId = weatherRepository.saveAndFlush(new Weather(
                UUID.randomUUID(),
                LocalDate.of(2026, 11, 12),
                Instant.parse("2026-11-12T00:00:00Z"),
                18.5,
                12.0,
                22.0,
                (short) 65,
                500,
                0.6,
                2.4,
                0.0,
                3.0,
                (short) 8
        )).getId();
    }

    @AfterEach
    void cleanUp() {
        // 이 테스트 전용 H2 DB의 데이터만 정리한다.
        notificationRepository.deleteAll();
        feedClothesRepository.deleteAll();
        feedRepository.deleteAll();
        followRepository.deleteAll();
        weatherRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("피드 커밋 후 작성자의 팔로워에게만 알림을 저장하고 전송한다")
    void notifiesFollowersAfterCommit() {
        FeedDto response = transactionTemplate.execute(status -> {
            FeedDto created = feedService.create(createRequest(), authorId);

            assertThat(notificationRepository.findAll()).isEmpty();
            verifyNoInteractions(notificationSseService);

            return created;
        });

        assertThat(response).isNotNull();
        assertThat(feedRepository.existsById(response.id())).isTrue();

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications)
                .extracting(Notification::getReceiverId)
                .containsExactlyInAnyOrder(firstFollowerId, secondFollowerId);

        ArgumentCaptor<UUID> receiverCaptor =
                ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<NotificationDto> notificationCaptor =
                ArgumentCaptor.forClass(NotificationDto.class);

        verify(notificationSseService, times(2))
                .send(receiverCaptor.capture(), notificationCaptor.capture());

        assertThat(receiverCaptor.getAllValues())
                .containsExactlyInAnyOrder(firstFollowerId, secondFollowerId);
        assertThat(notificationCaptor.getAllValues())
                .extracting(NotificationDto::id)
                .containsExactlyInAnyOrderElementsOf(
                        notifications.stream()
                                .map(Notification::getId)
                                .toList()
                );

        for (int index = 0; index < 2; index++) {
            assertThat(notificationCaptor.getAllValues().get(index).receiverId())
                    .isEqualTo(receiverCaptor.getAllValues().get(index));
        }
    }

    @Test
    @DisplayName("피드 등록이 롤백되면 피드와 알림이 저장되지 않고 전송도 하지 않는다")
    void doesNotNotifyAfterRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            feedService.create(createRequest(), authorId);
            status.setRollbackOnly();
        });

        assertThat(feedRepository.count()).isZero();
        assertThat(notificationRepository.findAll()).isEmpty();
        verifyNoInteractions(notificationSseService);
    }

    private FeedCreateRequest createRequest() {
        return new FeedCreateRequest(
                authorId,
                weatherId,
                List.of(),
                "오늘의 코디"
        );
    }

    private UUID createUser(String name) {
        return userRepository.saveAndFlush(User.create(
                name,
                UUID.randomUUID() + "@feed-follow-notification.test",
                "encoded-password"
        )).getId();
    }
}