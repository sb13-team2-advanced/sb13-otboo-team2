package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 좋아요 취소 서비스 테스트")
class FeedUnlikeServiceImplTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @InjectMocks
    private FeedLikeServiceImpl feedLikeService;

    @Test
    @DisplayName("로그인 사용자 ID가 없으면 DB 조회 전에 401 오류로 거절한다")
    void rejectsMissingAuthenticatedUser() {
        UUID feedId = UUID.randomUUID();

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedLikeService.unlike(feedId, null)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
        verifyNoInteractions(feedRepository, feedLikeRepository);
    }

    @Test
    @DisplayName("피드 ID가 없으면 DB 조회 전에 400 오류로 거절한다")
    void rejectsMissingFeedId() {
        UUID userId = UUID.randomUUID();

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedLikeService.unlike(null, userId)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(feedRepository, feedLikeRepository);
    }

    @Test
    @DisplayName("존재하지 않는 피드는 404 오류로 거절하고 좋아요 삭제를 시도하지 않는다")
    void rejectsMissingFeed() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(feedRepository.findByIdForUpdate(feedId)).thenReturn(Optional.empty());

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedLikeService.unlike(feedId, userId)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEED_NOT_FOUND);
        verifyNoInteractions(feedLikeRepository);
    }

    @Test
    @DisplayName("내가 누른 좋아요 한 건을 삭제하면 피드의 좋아요 수가 1 줄어든다")
    void removesMyLikeAndDecreasesCount() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Feed feed = feedWithThreeLikes();

        when(feedRepository.findByIdForUpdate(feedId)).thenReturn(Optional.of(feed));
        when(feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId)).thenReturn(1);

        feedLikeService.unlike(feedId, userId);

        verify(feedLikeRepository).deleteByFeedIdAndUserId(feedId, userId);
        assertThat(feed.getLikeCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("같은 사용자가 두 번 취소해도 좋아요 수는 한 번만 줄어든다")
    void repeatedUnlikeDecreasesCountOnlyOnce() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Feed feed = feedWithThreeLikes();

        when(feedRepository.findByIdForUpdate(feedId)).thenReturn(Optional.of(feed));
        when(feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId)).thenReturn(1, 0);

        feedLikeService.unlike(feedId, userId);
        feedLikeService.unlike(feedId, userId);

        assertThat(feed.getLikeCount()).isEqualTo(2L);
    }

    private Feed feedWithThreeLikes() {
        return Feed.builder()
                .authorId(UUID.randomUUID())
                .weatherId(UUID.randomUUID())
                .content("오늘의 코디")
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .likeCount(3L)
                .build();
    }
}