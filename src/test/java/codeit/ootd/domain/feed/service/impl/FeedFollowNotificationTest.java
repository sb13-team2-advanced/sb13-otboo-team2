package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.follow.repository.FollowRepository;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.mapper.WeatherDtoMapper;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("새 피드 팔로워 알림 테스트")
class FeedFollowNotificationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WeatherRepository weatherRepository;

    @Mock
    private ClothesRepository clothesRepository;

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedClothesRepository feedClothesRepository;

    @Mock
    private WeatherDtoMapper weatherDtoMapper;

    @Mock
    private FeedMapper feedMapper;

    @Mock
    private FeedResponseAssembler feedResponseAssembler;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FeedServiceImpl feedService;

    @Test
    @DisplayName("새 피드를 등록하면 작성자의 팔로워들에게 각각 알림을 요청한다")
    void notifiesEachFollowerWhenFeedIsCreated() {
        UUID authorId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID firstFollowerId = UUID.randomUUID();
        UUID secondFollowerId = UUID.randomUUID();

        FeedCreateRequest request = new FeedCreateRequest(
                authorId,
                weatherId,
                List.of(),
                "오늘의 코디"
        );

        User author = mock(User.class);
        Weather weather = mock(Weather.class);
        Feed feed = mock(Feed.class);

        when(userRepository.findById(authorId))
                .thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId))
                .thenReturn(Optional.of(weather));
        when(weather.getWeatherDate())
                .thenReturn(LocalDate.of(2026, 11, 12));
        when(clothesRepository.findAllById(request.clothesIds()))
                .thenReturn(List.of());
        when(feedMapper.toEntity(request, null))
                .thenReturn(feed);
        when(feedRepository.save(feed))
                .thenReturn(feed);
        when(followRepository.findFollowerIdsByFolloweeId(authorId))
                .thenReturn(List.of(firstFollowerId, secondFollowerId));

        feedService.create(request, authorId);

        ArgumentCaptor<NotificationRequestedEvent> captor =
                ArgumentCaptor.forClass(NotificationRequestedEvent.class);

        verify(eventPublisher, times(2)).publishEvent(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(NotificationRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(
                        firstFollowerId,
                        secondFollowerId
                );

        assertThat(captor.getAllValues()).allSatisfy(event -> {
            assertThat(event.title()).isNotBlank();
            assertThat(event.content()).isNotBlank();
            assertThat(event.level()).isEqualTo(NotificationLevel.INFO);
        });
    }
}