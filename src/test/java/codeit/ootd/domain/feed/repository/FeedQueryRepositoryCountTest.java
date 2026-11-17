package codeit.ootd.domain.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.global.enums.SortDirection;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-query-test;DB_CLOSE_DELAY=-1"
})
@Transactional
@DisplayName("피드 전체 개수 조회 테스트")
class FeedQueryRepositoryCountTest {

    private static final UUID AUTHOR_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID AUTHOR_B =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedQueryRepository feedQueryRepository;

    @BeforeEach
    void setUp() {
        saveFeed(AUTHOR_A, "파란 셔츠", "CLEAR", "NONE");
        saveFeed(AUTHOR_A, "흰 셔츠", "CLEAR", "NONE");
        saveFeed(AUTHOR_B, "다른 작성자 셔츠", "CLEAR", "NONE");
        saveFeed(AUTHOR_A, "검정 코트", "CLEAR", "NONE");
        saveFeed(AUTHOR_A, "흐린 날 셔츠", "CLOUDY", "NONE");
        saveFeed(AUTHOR_A, "비 오는 날 셔츠", "CLEAR", "RAIN");
    }

    @Test
    @DisplayName("검색 조건이 없으면 전체 피드 개수를 반환한다")
    void countsAllFeeds() {
        FeedSearchRequest request = request(
                null, null, null, null
        );

        assertThat(feedQueryRepository.countList(request)).isEqualTo(6);
    }

    @Test
    @DisplayName("검색 조건을 모두 적용하고 커서와 limit은 개수에서 제외한다")
    void countsFilteredFeedsWithoutPagination() {
        FeedSearchRequest request = request(
                "셔츠", "CLEAR", "NONE", AUTHOR_A
        );

        assertThat(feedQueryRepository.countList(request)).isEqualTo(2);
    }

    @Test
    @DisplayName("검색 조건에 맞는 피드가 없으면 0을 반환한다")
    void returnsZeroWhenNoFeedMatches() {
        FeedSearchRequest request = request(
                "없는 검색어", null, null, null
        );

        assertThat(feedQueryRepository.countList(request)).isZero();
    }

    private FeedSearchRequest request(
            String keywordLike,
            String skyStatusEqual,
            String precipitationTypeEqual,
            UUID authorIdEqual
    ) {
        return new FeedSearchRequest(
                "2000-01-01T00:00:00Z",
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                1,
                "createdAt",
                SortDirection.DESCENDING,
                keywordLike,
                skyStatusEqual,
                precipitationTypeEqual,
                authorIdEqual
        );
    }

    private void saveFeed(
            UUID authorId,
            String content,
            String skyStatus,
            String precipitationType
    ) {
        Feed feed = Feed.builder()
                .authorId(authorId)
                .weatherId(UUID.randomUUID())
                .content(content)
                .weatherSkyStatus(skyStatus)
                .weatherPrecipitationType(precipitationType)
                .build();

        feedRepository.saveAndFlush(feed);
    }
}