package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
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
@DisplayName("피드 삭제 요청 검증 테스트")
class FeedServiceImplDeleteValidationTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedClothesRepository feedClothesRepository;

    @InjectMocks
    private FeedServiceImpl feedService;

    @Test
    @DisplayName("로그인 사용자 ID가 없으면 DB 조회 전에 401로 거절한다")
    void rejectsMissingUserId() {
        assertThatThrownBy(() ->
                feedService.delete(UUID.randomUUID(), null)
        ).isInstanceOfSatisfying(
                OtbooException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED)
        );

        verifyNoInteractions(feedRepository, feedClothesRepository);
    }

    @Test
    @DisplayName("피드 ID가 없으면 DB 조회 전에 400으로 거절한다")
    void rejectsMissingFeedId() {
        assertThatThrownBy(() ->
                feedService.delete(null, UUID.randomUUID())
        ).isInstanceOfSatisfying(
                OtbooException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST)
        );

        verifyNoInteractions(feedRepository, feedClothesRepository);
    }

    @Test
    @DisplayName("존재하지 않는 피드는 404로 거절하고 삭제하지 않는다")
    void rejectsMissingFeed() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        given(feedRepository.findById(feedId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                feedService.delete(feedId, userId)
        ).isInstanceOfSatisfying(
                OtbooException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FEED_NOT_FOUND)
        );

        verify(feedRepository).findById(feedId);
        verifyNoMoreInteractions(feedRepository);
        verifyNoInteractions(feedClothesRepository);
    }

    @Test
    @DisplayName("다른 사람의 피드는 403으로 거절하고 삭제하지 않는다")
    void rejectsAnotherUsersFeed() {
        UUID feedId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Feed feed = Feed.builder()
                .authorId(authorId)
                .weatherId(UUID.randomUUID())
                .content("기존 내용")
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .build();

        given(feedRepository.findById(feedId))
                .willReturn(Optional.of(feed));

        assertThatThrownBy(() ->
                feedService.delete(feedId, requesterId)
        ).isInstanceOfSatisfying(
                OtbooException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED)
        );

        verify(feedRepository).findById(feedId);
        verifyNoMoreInteractions(feedRepository);
        verifyNoInteractions(feedClothesRepository);
    }
}