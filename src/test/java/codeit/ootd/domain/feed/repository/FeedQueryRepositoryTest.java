package codeit.ootd.domain.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
@DisplayName("피드 목록 조회 Repository 테스트")
class FeedQueryRepositoryTest {

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedQueryRepository feedQueryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("생성 시각 내림차순으로 지정한 개수만큼 조회한다")
    void findsLatestFeedsWithinSize() {
        UUID oldestId = saveFeed("오래된 피드", "2026-11-03T00:00:00Z", 0);
        UUID newestId = saveFeed("최근 피드", "2026-11-03T02:00:00Z", 0);
        UUID middleId = saveFeed("중간 피드", "2026-11-03T01:00:00Z", 0);
        entityManager.clear();

        List<Feed> result = feedQueryRepository.findList(
                request("createdAt", SortDirection.DESCENDING), 2
        );

        assertThat(result)
                .extracting(Feed::getId)
                .containsExactly(newestId, middleId)
                .doesNotContain(oldestId);
    }

    @Test
    @DisplayName("생성 시각 오름차순으로 지정한 개수만큼 조회한다")
    void findsOldestFeedsWithinSize() {
        UUID oldestId = saveFeed("오래된 피드", "2026-11-03T00:00:00Z", 0);
        UUID newestId = saveFeed("최근 피드", "2026-11-03T02:00:00Z", 0);
        UUID middleId = saveFeed("중간 피드", "2026-11-03T01:00:00Z", 0);
        entityManager.clear();

        List<Feed> result = feedQueryRepository.findList(
                request("createdAt", SortDirection.ASCENDING), 2
        );

        assertThat(result)
                .extracting(Feed::getId)
                .containsExactly(oldestId, middleId)
                .doesNotContain(newestId);
    }

    @Test
    @DisplayName("좋아요가 많은 순으로 지정한 개수만큼 조회한다")
    void findsMostLikedFeedsWithinSize() {
        UUID mostLikedId = saveFeed("좋아요 30개", "2026-11-03T00:00:00Z", 30);
        UUID leastLikedId = saveFeed("좋아요 10개", "2026-11-03T01:00:00Z", 10);
        UUID middleId = saveFeed("좋아요 20개", "2026-11-03T02:00:00Z", 20);
        entityManager.clear();

        List<Feed> result = feedQueryRepository.findList(
                request("likeCount", SortDirection.DESCENDING), 2
        );

        assertThat(result)
                .extracting(Feed::getId)
                .containsExactly(mostLikedId, middleId)
                .doesNotContain(leastLikedId);
    }

    @Test
    @DisplayName("좋아요가 적은 순으로 지정한 개수만큼 조회한다")
    void findsLeastLikedFeedsWithinSize() {
        UUID mostLikedId = saveFeed("좋아요 30개", "2026-11-03T00:00:00Z", 30);
        UUID leastLikedId = saveFeed("좋아요 10개", "2026-11-03T01:00:00Z", 10);
        UUID middleId = saveFeed("좋아요 20개", "2026-11-03T02:00:00Z", 20);
        entityManager.clear();

        List<Feed> result = feedQueryRepository.findList(
                request("likeCount", SortDirection.ASCENDING), 2
        );

        assertThat(result)
                .extracting(Feed::getId)
                .containsExactly(leastLikedId, middleId)
                .doesNotContain(mostLikedId);
    }

    private FeedSearchRequest request(
            String sortBy,
            SortDirection sortDirection
    ) {
        return new FeedSearchRequest(
                null,
                null,
                2,
                sortBy,
                sortDirection,
                null,
                null,
                null,
                null
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