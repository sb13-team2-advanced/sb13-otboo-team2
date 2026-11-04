package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.FeedDtoCursorResponse;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedQueryRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.global.enums.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 목록 다음 페이지 처리 테스트")
class FeedServiceImplListPaginationTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant CURSOR_TIME =
            Instant.parse("2026-11-04T01:00:00Z");

    @Mock
    private FeedQueryRepository feedQueryRepository;

    @Mock
    private FeedResponseAssembler feedResponseAssembler;

    @InjectMocks
    private FeedServiceImpl feedService;

    @ParameterizedTest(name = "{0}")
    @MethodSource("sortCases")
    @DisplayName("한 건을 더 조회하면 요청 개수만 반환하고 마지막 반환 항목으로 커서를 만든다")
    void returnsRequestedSizeAndNextCursor(
            String description,
            String sortBy,
            SortDirection direction,
            String expectedCursor
    ) {
        FeedSearchRequest request = request(sortBy, direction);
        boolean ascending = direction == SortDirection.ASCENDING;

        Feed first = feed(
                ascending
                        ? CURSOR_TIME.minusSeconds(60)
                        : CURSOR_TIME.plusSeconds(60),
                ascending ? 7L : 9L
        );
        Feed second = feed(CURSOR_TIME, 8L);
        Feed extra = feed(
                ascending
                        ? CURSOR_TIME.plusSeconds(60)
                        : CURSOR_TIME.minusSeconds(60),
                ascending ? 9L : 7L
        );

        FeedDto firstDto = mock(FeedDto.class);
        FeedDto secondDto = mock(FeedDto.class);
        FeedDto extraDto = mock(FeedDto.class);

        Map<UUID, FeedDto> responsesById = Map.of(
                first.getId(), firstDto,
                second.getId(), secondDto,
                extra.getId(), extraDto
        );

        given(feedQueryRepository.findList(request, 3))
                .willReturn(List.of(first, second, extra));
        given(feedQueryRepository.countList(request))
                .willReturn(3L);

        // 전달받은 피드에 해당하는 응답만 돌려주도록 구성한다.
        given(feedResponseAssembler.toFeedDtos(anyList(), eq(USER_ID)))
                .willAnswer(invocation -> {
                    List<Feed> page = invocation.getArgument(0);
                    return page.stream()
                            .map(item -> responsesById.get(item.getId()))
                            .toList();
                });

        FeedDtoCursorResponse response =
                feedService.list(request, USER_ID);

        assertThat(response.data()).containsExactly(firstDto, secondDto);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(expectedCursor);
        assertThat(response.nextIdAfter()).isEqualTo(second.getId());
        assertThat(response.totalCount()).isEqualTo(3L);
        assertThat(response.sortBy()).isEqualTo(sortBy);
        assertThat(response.sortDirection()).isEqualTo(direction);

        verify(feedQueryRepository).findList(request, 3);
        verify(feedResponseAssembler)
                .toFeedDtos(List.of(first, second), USER_ID);
    }

    @Test
    @DisplayName("조회 개수가 요청 개수와 정확히 같으면 다음 페이지가 없다")
    void hasNoNextPageWhenResultSizeEqualsLimit() {
        FeedSearchRequest request = request(
                "createdAt",
                SortDirection.DESCENDING
        );

        Feed first = feed(CURSOR_TIME.plusSeconds(60), 9L);
        Feed second = feed(CURSOR_TIME, 8L);
        List<Feed> feeds = List.of(first, second);

        FeedDto firstDto = mock(FeedDto.class);
        FeedDto secondDto = mock(FeedDto.class);

        given(feedQueryRepository.findList(request, 3))
                .willReturn(feeds);
        given(feedQueryRepository.countList(request))
                .willReturn(2L);
        given(feedResponseAssembler.toFeedDtos(feeds, USER_ID))
                .willReturn(List.of(firstDto, secondDto));

        FeedDtoCursorResponse response =
                feedService.list(request, USER_ID);

        assertThat(response.data()).containsExactly(firstDto, secondDto);
        assertThat(response.totalCount()).isEqualTo(2L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();

        verify(feedResponseAssembler).toFeedDtos(feeds, USER_ID);
    }

    private static Stream<Arguments> sortCases() {
        return Stream.of(
                Arguments.of(
                        "생성 시각 내림차순",
                        "createdAt",
                        SortDirection.DESCENDING,
                        CURSOR_TIME.toString()
                ),
                Arguments.of(
                        "생성 시각 오름차순",
                        "createdAt",
                        SortDirection.ASCENDING,
                        CURSOR_TIME.toString()
                ),
                Arguments.of(
                        "좋아요 내림차순",
                        "likeCount",
                        SortDirection.DESCENDING,
                        "8"
                ),
                Arguments.of(
                        "좋아요 오름차순",
                        "likeCount",
                        SortDirection.ASCENDING,
                        "8"
                )
        );
    }

    private FeedSearchRequest request(
            String sortBy,
            SortDirection direction
    ) {
        return new FeedSearchRequest(
                null,
                null,
                2,
                sortBy,
                direction,
                null,
                null,
                null,
                null
        );
    }

    private Feed feed(Instant createdAt, long likeCount) {
        Feed feed = Feed.builder()
                .authorId(USER_ID)
                .weatherId(UUID.randomUUID())
                .content("오늘의 코디")
                .likeCount(likeCount)
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .build();

        ReflectionTestUtils.setField(feed, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(feed, "createdAt", createdAt);

        return feed;
    }
}