package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedComment;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
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
@DisplayName("피드 댓글 알림 테스트")
class FeedCommentNotificationTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedCommentRepository feedCommentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FeedResponseAssembler feedResponseAssembler;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private FeedMapper feedMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FeedCommentServiceImpl feedCommentService;

    @Test
    @DisplayName("다른 사용자의 피드에 댓글을 등록하면 피드 작성자에게 알림을 요청한다")
    void publishesNotificationToFeedAuthor() {
        UUID feedId = UUID.randomUUID();
        UUID feedAuthorId = UUID.randomUUID();
        UUID commentAuthorId = UUID.randomUUID();

        Feed feed = Feed.builder()
                .authorId(feedAuthorId)
                .weatherId(UUID.randomUUID())
                .content("오늘의 코디")
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .build();

        User commentAuthor = mock(User.class);
        CommentCreateRequest request = new CommentCreateRequest(
                feedId,
                commentAuthorId,
                "잘 어울려요!"
        );

        when(feedRepository.findByIdForUpdate(feedId))
                .thenReturn(Optional.of(feed));
        when(userRepository.findById(commentAuthorId))
                .thenReturn(Optional.of(commentAuthor));
        when(feedCommentRepository.save(any(FeedComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        feedCommentService.create(feedId, request, commentAuthorId);

        ArgumentCaptor<NotificationRequestedEvent> captor =
                ArgumentCaptor.forClass(NotificationRequestedEvent.class);

        verify(eventPublisher).publishEvent(captor.capture());

        NotificationRequestedEvent event = captor.getValue();

        assertThat(event.receiverId()).isEqualTo(feedAuthorId);
        assertThat(event.title()).isNotBlank();
        assertThat(event.content()).isNotBlank();
        assertThat(event.level()).isEqualTo(NotificationLevel.INFO);
    }
}