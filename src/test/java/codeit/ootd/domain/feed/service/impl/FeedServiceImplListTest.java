package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.FeedDtoCursorResponse;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedQueryRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 목록 조회 서비스 테스트")
class FeedServiceImplListTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private FeedQueryRepository feedQueryRepository;

    @Mock
    private FeedResponseAssembler feedResponseAssembler;

    @InjectMocks
    private FeedServiceImpl feedService;

    @Test
    @DisplayName("검색 결과가 없으면 빈 목록과 다음 페이지가 없는 응답을 반환한다")
    void returnsEmptyPageWhenNoFeedsMatch() {
        FeedSearchRequest request = request();

        given(feedQueryRepository.findList(request, 21))
                .willReturn(List.of());
        given(feedQueryRepository.countList(request))
                .willReturn(0L);

        FeedDtoCursorResponse response =
                feedService.list(request, USER_ID);

        assertThat(response.data()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();
        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.sortDirection())
                .isEqualTo(SortDirection.DESCENDING);

        verify(feedQueryRepository).findList(request, 21);
        verify(feedQueryRepository).countList(request);
        verifyNoInteractions(feedResponseAssembler);
    }

    @Test
    @DisplayName("조회 결과가 있으면 사용자 ID를 전달해 구성한 응답을 반환한다")
    void returnsAssembledFeedsForCurrentUser() {
        FeedSearchRequest request = request();

        Feed firstFeed = mock(Feed.class);
        Feed secondFeed = mock(Feed.class);
        List<Feed> feeds = List.of(firstFeed, secondFeed);

        FeedDto firstDto = mock(FeedDto.class);
        FeedDto secondDto = mock(FeedDto.class);
        List<FeedDto> data = List.of(firstDto, secondDto);

        given(feedQueryRepository.findList(request, 21))
                .willReturn(feeds);
        given(feedQueryRepository.countList(request))
                .willReturn(2L);
        given(feedResponseAssembler.toFeedDtos(feeds, USER_ID))
                .willReturn(data);

        FeedDtoCursorResponse response =
                feedService.list(request, USER_ID);

        assertThat(response.data())
                .containsExactly(firstDto, secondDto);
        assertThat(response.totalCount()).isEqualTo(2L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();
        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.sortDirection())
                .isEqualTo(SortDirection.DESCENDING);

        verify(feedResponseAssembler).toFeedDtos(feeds, USER_ID);
    }

    private FeedSearchRequest request() {
        return new FeedSearchRequest(
                null,
                null,
                20,
                "createdAt",
                SortDirection.DESCENDING,
                null,
                null,
                null,
                null
        );
    }
}