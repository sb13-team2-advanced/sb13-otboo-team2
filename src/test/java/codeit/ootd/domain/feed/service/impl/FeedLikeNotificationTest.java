package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
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
@DisplayName("피드 좋아요 알림 테스트")
class FeedLikeNotificationTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FeedLikeServiceImpl feedLikeService;

    @Test
    @DisplayName("다른 사용자의 피드에 새 좋아요를 등록하면 작성자에게 알림을 요청한다")
    void publishesNotificationToFeedAuthor() {
        UUID feedId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Feed feed = Feed.builder()
                .authorId(authorId)
                .weatherId(UUID.randomUUID())
                .content("오늘의 코디")
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .build();

        when(feedRepository.findByIdForUpdate(feedId))
                .thenReturn(Optional.of(feed));
        when(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId))
                .thenReturn(false);

        feedLikeService.like(feedId, userId);

        ArgumentCaptor<NotificationRequestedEvent> captor =
                ArgumentCaptor.forClass(NotificationRequestedEvent.class);

        verify(eventPublisher).publishEvent(captor.capture());

        NotificationRequestedEvent event = captor.getValue();
        assertThat(event.receiverId()).isEqualTo(authorId);
        assertThat(event.title()).isNotBlank();
        assertThat(event.content()).isNotBlank();
    }
}