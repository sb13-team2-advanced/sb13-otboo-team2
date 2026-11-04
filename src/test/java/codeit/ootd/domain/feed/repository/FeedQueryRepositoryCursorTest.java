package codeit.ootd.domain.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
@DisplayName("피드 목록 커서 조회 테스트")
class FeedQueryRepositoryCursorTest {

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedQueryRepository feedQueryRepository;

    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest(name = "{0}")
    @MethodSource("cursorCases")
    @DisplayName("정렬 방향에 따라 커서 다음 피드만 조회한다")
    void findsFeedsAfterCursor(
            String description,
            String sortBy,
            SortDirection sortDirection,
            String cursor,
            String expectedContent
    ) {
        saveFeed("첫 번째 피드", "2026-11-03T00:00:00Z", 10);

        UUID middleId = saveFeed(
                "중간 피드",
                "2026-11-03T01:00:00Z",
                20
        );

        saveFeed("마지막 피드", "2026-11-03T02:00:00Z", 30);

        entityManager.clear();

        FeedSearchRequest request = new FeedSearchRequest(
                cursor,
                middleId,
                10,
                sortBy,
                sortDirection,
                null,
                null,
                null,
                null
        );

        List<Feed> result = feedQueryRepository.findList(request, 10);

        assertThat(result)
                .extracting(Feed::getContent)
                .containsExactly(expectedContent);
    }

    private static Stream<Arguments> cursorCases() {
        return Stream.of(
                Arguments.of(
                        "최신순에서는 커서보다 오래된 피드를 조회",
                        "createdAt",
                        SortDirection.DESCENDING,
                        "2026-11-03T01:00:00Z",
                        "첫 번째 피드"
                ),
                Arguments.of(
                        "오래된 순에서는 커서보다 최신 피드를 조회",
                        "createdAt",
                        SortDirection.ASCENDING,
                        "2026-11-03T01:00:00Z",
                        "마지막 피드"
                ),
                Arguments.of(
                        "좋아요 내림차순에서는 커서보다 적은 피드를 조회",
                        "likeCount",
                        SortDirection.DESCENDING,
                        "20",
                        "첫 번째 피드"
                ),
                Arguments.of(
                        "좋아요 오름차순에서는 커서보다 많은 피드를 조회",
                        "likeCount",
                        SortDirection.ASCENDING,
                        "20",
                        "마지막 피드"
                )
        );
    }

    private UUID saveFeed(
            String content,
            String createdAt,
            long likeCount
    ) {
        Feed feed = Feed.builder()
                .authorId(UUID.randomUUID())
                .weatherId(UUID.randomUUID())
                .content(content)
                .likeCount(likeCount)
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .build();

        Feed saved = feedRepository.saveAndFlush(feed);

        entityManager.createNativeQuery("""
                UPDATE feeds
                SET created_at = :createdAt
                WHERE id = :id
                """)
                .setParameter("createdAt", Instant.parse(createdAt))
                .setParameter("id", saved.getId())
                .executeUpdate();

        return saved.getId();
    }
}