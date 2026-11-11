package codeit.ootd.domain.feed.service.impl;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 삭제 서비스 테스트")
class FeedServiceImplDeleteTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedClothesRepository feedClothesRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @Mock
    private FeedCommentRepository feedCommentRepository;

    @InjectMocks
    private FeedServiceImpl feedService;

    @Test
    @DisplayName("작성자 본인이 요청하면 연결 데이터를 정리한 뒤 피드를 삭제한다")
    void deletesRelatedDataBeforeFeed() {
        UUID feedId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Feed feed = Feed.builder()
                .authorId(authorId)
                .weatherId(UUID.randomUUID())
                .content("삭제할 피드")
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .build();

        given(feedRepository.findById(feedId))
                .willReturn(Optional.of(feed));

        feedService.delete(feedId, authorId);

        InOrder deletionOrder = inOrder(
                feedRepository,
                feedClothesRepository,
                feedLikeRepository,
                feedCommentRepository
        );

        deletionOrder.verify(feedRepository).findById(feedId);
        deletionOrder.verify(feedClothesRepository).deleteAllByFeedId(feedId);
        deletionOrder.verify(feedLikeRepository).deleteAllByFeedId(feedId);
        deletionOrder.verify(feedCommentRepository).deleteAllByFeedId(feedId);
        deletionOrder.verify(feedRepository).delete(feed);

        verifyNoMoreInteractions(
                feedRepository,
                feedClothesRepository,
                feedLikeRepository,
                feedCommentRepository
        );
    }
}