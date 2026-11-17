package codeit.ootd.domain.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
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
@DisplayName("피드 목록 커서 동점 처리 테스트")
class FeedQueryRepositoryCursorTieTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-03T01:00:00Z");

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedQueryRepository feedQueryRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        for (int index = 1; index <= 5; index++) {
            Feed feed = Feed.builder()
                    .authorId(UUID.randomUUID())
                    .weatherId(UUID.randomUUID())
                    .content("동점 피드 " + index)
                    .likeCount(20)
                    .weatherSkyStatus("CLEAR")
                    .weatherPrecipitationType("NONE")
                    .build();

            Feed saved = feedRepository.saveAndFlush(feed);

            entityManager.createNativeQuery("""
                    UPDATE feeds
                    SET created_at = :createdAt
                    WHERE id = :id
                    """)
                    .setParameter("createdAt", CREATED_AT)
                    .setParameter("id", saved.getId())
                    .executeUpdate();
        }

        entityManager.clear();
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("sortCases")
    @DisplayName("정렬 값이 같아도 다음 페이지를 중복과 누락 없이 조회한다")
    void paginatesTiedFeeds(
            String sortBy,
            SortDirection sortDirection
    ) {
        List<Feed> allFeeds = feedQueryRepository.findList(
                request(sortBy, sortDirection, null, null, 10),
                10
        );

        assertThat(allFeeds).hasSize(5);

        List<UUID> collectedIds = new ArrayList<>();
        String cursor = null;
        UUID idAfter = null;

        for (int pageIndex = 0; pageIndex < 3; pageIndex++) {
            List<Feed> page = feedQueryRepository.findList(
                    request(sortBy, sortDirection, cursor, idAfter, 2),
                    2
            );

            int expectedSize = pageIndex < 2 ? 2 : 1;
            assertThat(page).hasSize(expectedSize);

            collectedIds.addAll(
                    page.stream()
                            .map(Feed::getId)
                            .toList()
            );

            Feed lastFeed = page.get(page.size() - 1);

            cursor = "createdAt".equals(sortBy)
                    ? lastFeed.getCreatedAt().toString()
                    : Long.toString(lastFeed.getLikeCount());

            idAfter = lastFeed.getId();
        }

        assertThat(collectedIds)
                .doesNotHaveDuplicates()
                .containsExactlyElementsOf(
                        allFeeds.stream()
                                .map(Feed::getId)
                                .toList()
                );

        List<Feed> afterLastPage = feedQueryRepository.findList(
                request(sortBy, sortDirection, cursor, idAfter, 2),
                2
        );

        assertThat(afterLastPage).isEmpty();
    }

    private static Stream<Arguments> sortCases() {
        return Stream.of(
                Arguments.of("createdAt", SortDirection.DESCENDING),
                Arguments.of("createdAt", SortDirection.ASCENDING),
                Arguments.of("likeCount", SortDirection.DESCENDING),
                Arguments.of("likeCount", SortDirection.ASCENDING)
        );
    }

    private FeedSearchRequest request(
            String sortBy,
            SortDirection sortDirection,
            String cursor,
            UUID idAfter,
            int limit
    ) {
        return new FeedSearchRequest(
                cursor,
                idAfter,
                limit,
                sortBy,
                sortDirection,
                null,
                null,
                null,
                null
        );
    }
}
