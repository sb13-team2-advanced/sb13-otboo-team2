package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedLike;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 좋아요 등록 서비스 테스트")
class FeedLikeServiceImplTest {

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
                () -> feedLikeService.like(feedId, null)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);

        verifyNoInteractions(feedRepository, feedLikeRepository);
    }

    @Test
    @DisplayName("피드 ID가 없으면 DB 조회 전에 400 오류로 거절한다")
    void rejectsMissingFeedId() {
        UUID userId = UUID.randomUUID();

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedLikeService.like(null, userId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verifyNoInteractions(feedRepository, feedLikeRepository);
    }

    @Test
    @DisplayName("존재하지 않는 피드는 404 오류로 거절한다")
    void rejectsMissingFeed() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(feedRepository.findByIdForUpdate(feedId))
                .thenReturn(Optional.empty());

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedLikeService.like(feedId, userId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FEED_NOT_FOUND);

        verifyNoInteractions(feedLikeRepository);
    }

    @Test
    @DisplayName("새 좋아요를 저장하고 기존 좋아요 수를 1 늘린다")
    void savesLikeAndIncreasesCount() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Feed feed = feedWithLikeCount(3L);

        when(feedRepository.findByIdForUpdate(feedId))
                .thenReturn(Optional.of(feed));

        when(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId))
                .thenReturn(false);

        feedLikeService.like(feedId, userId);

        ArgumentCaptor<FeedLike> captor =
                ArgumentCaptor.forClass(FeedLike.class);

        verify(feedLikeRepository).save(captor.capture());

        FeedLike savedLike = captor.getValue();

        assertThat(savedLike.getFeedId()).isEqualTo(feedId);
        assertThat(savedLike.getUserId()).isEqualTo(userId);
        assertThat(feed.getLikeCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("이미 좋아요를 누른 사용자는 추가 저장이나 개수 증가 없이 종료한다")
    void ignoresExistingLike() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Feed feed = feedWithLikeCount(3L);

        when(feedRepository.findByIdForUpdate(feedId))
                .thenReturn(Optional.of(feed));

        when(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId))
                .thenReturn(true);

        feedLikeService.like(feedId, userId);

        assertThat(feed.getLikeCount()).isEqualTo(3L);

        verify(feedLikeRepository)
                .existsByFeedIdAndUserId(feedId, userId);
        verifyNoMoreInteractions(feedLikeRepository);
    }

    private Feed feedWithLikeCount(long likeCount) {
        return Feed.builder()
                .authorId(UUID.randomUUID())
                .weatherId(UUID.randomUUID())
                .content("오늘의 코디")
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .likeCount(likeCount)
                .build();
    }
}