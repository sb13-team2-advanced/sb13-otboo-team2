package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.entity.FeedComment;
import codeit.ootd.domain.feed.entity.FeedLike;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-delete-integration;DB_CLOSE_DELAY=-1"
})
@DisplayName("피드 삭제 통합 테스트")
class FeedServiceImplDeleteIntegrationTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClothesRepository clothesRepository;

    @MockitoSpyBean
    private FeedRepository feedRepository;

    @Autowired
    private FeedClothesRepository feedClothesRepository;

    @Autowired
    private FeedLikeRepository feedLikeRepository;

    @Autowired
    private FeedCommentRepository feedCommentRepository;

    private UUID authorId;
    private UUID clothesId;
    private Fixture target;
    private Fixture other;

    @BeforeEach
    void setUp() {
        User author = userRepository.saveAndFlush(
                User.create(
                        "삭제 테스트 작성자",
                        UUID.randomUUID() + "@feed-delete.test",
                        "encoded-password"
                )
        );
        authorId = author.getId();

        Clothes clothes = clothesRepository.saveAndFlush(
                new Clothes(
                        authorId,
                        "셔츠",
                        ClothesType.TOP,
                        "/images/shirt.png"
                )
        );
        clothesId = clothes.getId();

        target = createFixture("삭제할 피드");
        other = createFixture("유지할 피드");
    }

    @AfterEach
    void cleanUp() {
        feedCommentRepository.deleteAll();
        feedLikeRepository.deleteAll();
        feedClothesRepository.deleteAll();
        feedRepository.deleteAll();
        clothesRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("대상 피드와 연결 데이터만 삭제하고 다른 피드와 의상 원본은 유지한다")
    void deletesOnlyTargetFeedAndRelatedData() {
        feedService.delete(target.feedId(), authorId);

        assertFixtureExists(target, false);
        assertFixtureExists(other, true);

        assertThat(clothesRepository.existsById(clothesId)).isTrue();
        assertThat(userRepository.existsById(authorId)).isTrue();
    }

    @Test
    @DisplayName("피드 삭제에 실패하면 먼저 삭제한 연결 데이터도 롤백된다")
    void rollsBackRelatedDataWhenFeedDeletionFails() {
        doAnswer(invocation -> {
            Feed feed = invocation.getArgument(0);
            assertThat(feed.getId()).isEqualTo(target.feedId());

            // 서비스 트랜잭션 안에서 연결 데이터 삭제가 실행됐는지 확인한다.
            assertThat(feedClothesRepository.existsById(target.linkId()))
                    .isFalse();
            assertThat(feedLikeRepository.existsById(target.likeId()))
                    .isFalse();
            assertThat(feedCommentRepository.existsById(target.commentId()))
                    .isFalse();

            throw new IllegalStateException("피드 삭제 실패 테스트");
        }).when(feedRepository).delete(any(Feed.class));

        assertThatThrownBy(() ->
                feedService.delete(target.feedId(), authorId)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("피드 삭제 실패 테스트");

        // 테스트 자체에는 트랜잭션이 없어 서비스 롤백 이후 DB를 다시 읽는다.
        assertFixtureExists(target, true);
        assertFixtureExists(other, true);

        assertThat(clothesRepository.existsById(clothesId)).isTrue();
    }

    private Fixture createFixture(String content) {
        Feed feed = feedRepository.saveAndFlush(
                Feed.builder()
                        .authorId(authorId)
                        .weatherId(UUID.randomUUID())
                        .content(content)
                        .weatherSkyStatus("CLEAR")
                        .weatherPrecipitationType("NONE")
                        .weatherTemperatureCurrent(20.0)
                        .weatherTemperatureMin(15.0)
                        .weatherTemperatureMax(25.0)
                        .build()
        );

        FeedClothes link = feedClothesRepository.saveAndFlush(
                new FeedClothes(feed.getId(), clothesId, 0)
        );
        FeedLike like = feedLikeRepository.saveAndFlush(
                new FeedLike(feed.getId(), authorId)
        );
        FeedComment comment = feedCommentRepository.saveAndFlush(
                new FeedComment(feed.getId(), authorId, "테스트 댓글")
        );

        return new Fixture(
                feed.getId(),
                link.getId(),
                like.getId(),
                comment.getId()
        );
    }

    private void assertFixtureExists(Fixture fixture, boolean expected) {
        assertThat(feedRepository.existsById(fixture.feedId()))
                .isEqualTo(expected);
        assertThat(feedClothesRepository.existsById(fixture.linkId()))
                .isEqualTo(expected);
        assertThat(feedLikeRepository.existsById(fixture.likeId()))
                .isEqualTo(expected);
        assertThat(feedCommentRepository.existsById(fixture.commentId()))
                .isEqualTo(expected);
    }

    private record Fixture(
            UUID feedId,
            UUID linkId,
            UUID likeId,
            UUID commentId
    ) {
    }
}