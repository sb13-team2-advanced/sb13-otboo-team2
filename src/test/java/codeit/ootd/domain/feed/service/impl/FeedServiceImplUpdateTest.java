package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.feed.dto.request.FeedUpdateRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 수정 서비스 테스트")
class FeedServiceImplUpdateTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedResponseAssembler feedResponseAssembler;

    @InjectMocks
    private FeedServiceImpl feedService;

    @Test
    @DisplayName("다른 사람의 피드는 수정하지 않고 403 오류로 거절한다")
    void rejectsUpdatingAnotherUsersFeed() {
        UUID feedId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Feed feed = feed(authorId);

        given(feedRepository.findById(feedId))
                .willReturn(Optional.of(feed));

        FeedUpdateRequest request =
                new FeedUpdateRequest("변경할 내용");

        assertThatThrownBy(() ->
                feedService.update(feedId, request, requesterId)
        ).isInstanceOfSatisfying(
                OtbooException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED)
        );

        assertThat(feed.getContent()).isEqualTo("기존 내용");
        verifyNoInteractions(feedResponseAssembler);
    }

    @Test
    @DisplayName("작성자 본인이 요청하면 내용을 수정하고 피드 응답을 반환한다")
    void updatesContentAndReturnsResponse() {
        UUID feedId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Feed feed = feed(authorId);
        UUID originalWeatherId = feed.getWeatherId();

        FeedUpdateRequest request =
                new FeedUpdateRequest("수정한 내용");
        FeedDto expectedResponse = mock(FeedDto.class);

        given(feedRepository.findById(feedId))
                .willReturn(Optional.of(feed));

        given(feedResponseAssembler.toFeedDtos(List.of(feed), authorId))
                .willAnswer(invocation -> {
                    assertThat(feed.getContent()).isEqualTo("수정한 내용");
                    return List.of(expectedResponse);
                });

        FeedDto response =
                feedService.update(feedId, request, authorId);

        assertThat(response).isSameAs(expectedResponse);
        assertThat(feed.getContent()).isEqualTo("수정한 내용");
        assertThat(feed.getAuthorId()).isEqualTo(authorId);
        assertThat(feed.getWeatherId()).isEqualTo(originalWeatherId);

        verify(feedResponseAssembler)
                .toFeedDtos(List.of(feed), authorId);
    }

    private Feed feed(UUID authorId) {
        return Feed.builder()
                .content("기존 내용")
                .authorId(authorId)
                .weatherId(UUID.randomUUID())
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .build();
    }
}