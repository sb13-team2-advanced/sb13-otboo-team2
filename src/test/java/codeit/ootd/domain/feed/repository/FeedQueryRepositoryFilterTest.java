package codeit.ootd.domain.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-query-test;DB_CLOSE_DELAY=-1"
})
@Transactional
@DisplayName("피드 목록 검색 조건 테스트")
class FeedQueryRepositoryFilterTest {

    private static final UUID AUTHOR_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID AUTHOR_B =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedQueryRepository feedQueryRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        saveFeed(AUTHOR_A, "파란 셔츠", "CLEAR", "NONE");
        saveFeed(AUTHOR_B, "흰 셔츠", "CLEAR", "RAIN");
        saveFeed(AUTHOR_A, "검정 코트", "CLOUDY", "NONE");

        entityManager.clear();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("searchCases")
    @DisplayName("검색 조건에 맞는 피드만 조회한다")
    void filtersFeeds(
            String description,
            FeedSearchRequest request,
            List<String> expectedContents
    ) {
        List<Feed> result = feedQueryRepository.findList(request, 10);

        assertThat(result)
                .extracting(Feed::getContent)
                .containsExactlyInAnyOrderElementsOf(expectedContents);
    }

    private static Stream<Arguments> searchCases() {
        return Stream.of(
                Arguments.of(
                        "내용에 검색어가 포함된 피드만 조회",
                        request("셔츠", null, null, null),
                        List.of("파란 셔츠", "흰 셔츠")
                ),
                Arguments.of(
                        "지정한 작성자의 피드만 조회",
                        request(null, null, null, AUTHOR_A),
                        List.of("파란 셔츠", "검정 코트")
                ),
                Arguments.of(
                        "하늘 상태가 일치하는 피드만 조회",
                        request(null, "CLEAR", null, null),
                        List.of("파란 셔츠", "흰 셔츠")
                ),
                Arguments.of(
                        "강수 유형이 일치하는 피드만 조회",
                        request(null, null, "RAIN", null),
                        List.of("흰 셔츠")
                ),
                Arguments.of(
                        "여러 조건을 모두 만족하는 피드만 조회",
                        request("셔츠", "CLEAR", "NONE", AUTHOR_A),
                        List.of("파란 셔츠")
                )
        );
    }

    private static FeedSearchRequest request(
            String keywordLike,
            String skyStatusEqual,
            String precipitationTypeEqual,
            UUID authorIdEqual
    ) {
        return new FeedSearchRequest(
                null,
                null,
                10,
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