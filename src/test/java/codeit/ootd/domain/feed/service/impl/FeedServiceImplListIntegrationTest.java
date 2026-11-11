package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.FeedDtoCursorResponse;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.entity.FeedLike;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
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
        "spring.datasource.url=jdbc:h2:mem:feed-list-integration;DB_CLOSE_DELAY=-1"
})
@Transactional
@DisplayName("피드 목록 조회 통합 테스트")
class FeedServiceImplListIntegrationTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ClothesRepository clothesRepository;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedClothesRepository feedClothesRepository;

    @Autowired
    private FeedLikeRepository feedLikeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("실제 저장된 피드를 응답으로 구성하고 다음 페이지까지 중복 없이 조회한다")
    void listsFeedsAcrossPagesWithRelatedData() {
        User author = userRepository.save(
                User.create("작성자", "author@feed-list.test", "encoded")
        );
        User viewer = userRepository.save(
                User.create("조회자", "viewer@feed-list.test", "encoded")
        );

        UUID authorId = author.getId();
        UUID viewerId = viewer.getId();

        Profile profile = new Profile(authorId);
        profile.changeProfileImageUrl("/images/author.png");
        profileRepository.save(profile);

        Clothes shirt = clothesRepository.save(
                new Clothes(
                        authorId, "셔츠", ClothesType.TOP, "/images/shirt.png"
                )
        );
        Clothes pants = clothesRepository.save(
                new Clothes(
                        authorId, "바지", ClothesType.BOTTOM, "/images/pants.png"
                )
        );

        UUID oldestId = saveFeed(
                authorId, "오래된 피드", "2026-11-04T00:00:00Z", 1L
        );
        UUID middleId = saveFeed(
                authorId, "중간 피드", "2026-11-04T01:00:00Z", 0L
        );
        UUID newestId = saveFeed(
                authorId, "최근 피드", "2026-11-04T02:00:00Z", 1L
        );

        // 저장 순서와 관계없이 displayOrder 순서로 응답해야 한다.
        feedClothesRepository.saveAll(List.of(
                new FeedClothes(newestId, pants.getId(), 1),
                new FeedClothes(newestId, shirt.getId(), 0)
        ));

        feedLikeRepository.saveAll(List.of(
                new FeedLike(newestId, viewerId),
                new FeedLike(oldestId, authorId)
        ));

        UUID shirtId = shirt.getId();
        UUID pantsId = pants.getId();

        entityManager.flush();
        entityManager.clear();

        FeedDtoCursorResponse firstPage = feedService.list(
                request(null, null),
                viewerId
        );

        assertThat(firstPage.data())
                .extracting(FeedDto::id)
                .containsExactly(newestId, middleId);
        assertThat(firstPage.totalCount()).isEqualTo(3L);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor())
                .isEqualTo("2026-11-04T01:00:00Z");
        assertThat(firstPage.nextIdAfter()).isEqualTo(middleId);

        FeedDto newest = firstPage.data().get(0);

        assertThat(newest.author().userId()).isEqualTo(authorId);
        assertThat(newest.author().name()).isEqualTo("작성자");
        assertThat(newest.author().profileImageUrl())
                .isEqualTo("/images/author.png");
        assertThat(newest.ootds())
                .extracting(OotdDto::clothesId)
                .containsExactly(shirtId, pantsId);
        assertThat(newest.likedByMe()).isTrue();
        assertThat(newest.likeCount()).isEqualTo(1L);
        assertThat(newest.weather().temperature().current())
                .isEqualTo(20.0);
        assertThat(newest.weather().temperature().comparedToDayBefore())
                .isNull();

        FeedDto middle = firstPage.data().get(1);
        assertThat(middle.ootds()).isEmpty();
        assertThat(middle.likedByMe()).isFalse();

        FeedDtoCursorResponse secondPage = feedService.list(
                request(
                        firstPage.nextCursor(),
                        firstPage.nextIdAfter()
                ),
                viewerId
        );

        assertThat(secondPage.data())
                .extracting(FeedDto::id)
                .containsExactly(oldestId);
        assertThat(secondPage.totalCount()).isEqualTo(3L);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(secondPage.nextIdAfter()).isNull();

        // 다른 사람이 누른 좋아요는 내 좋아요로 표시하면 안 된다.
        assertThat(secondPage.data().get(0).likeCount()).isEqualTo(1L);
        assertThat(secondPage.data().get(0).likedByMe()).isFalse();
    }

    private FeedSearchRequest request(String cursor, UUID idAfter) {
        return new FeedSearchRequest(
                cursor,
                idAfter,
                2,
                "createdAt",
                SortDirection.DESCENDING,
                null,
                null,
                null,
                null
        );
    }

    private UUID saveFeed(
            UUID authorId,
            String content,
            String createdAt,
            long likeCount
    ) {
        Feed feed = Feed.builder()
                .authorId(authorId)
                .weatherId(UUID.randomUUID())
                .content(content)
                .likeCount(likeCount)
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .weatherTemperatureCurrent(20.0)
                .weatherTemperatureMin(15.0)
                .weatherTemperatureMax(25.0)
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