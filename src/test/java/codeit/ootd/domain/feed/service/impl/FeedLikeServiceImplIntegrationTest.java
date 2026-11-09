package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedLikeService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-like-integration;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
@DisplayName("피드 좋아요 등록 통합 테스트")
class FeedLikeServiceImplIntegrationTest {

    @Autowired
    private FeedLikeService feedLikeService;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedLikeRepository feedLikeRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID feedId;
    private UUID firstUserId;
    private UUID secondUserId;

    @BeforeEach
    void setUp() {
        UUID authorId = createUser("피드 작성자");
        firstUserId = createUser("첫 번째 사용자");
        secondUserId = createUser("두 번째 사용자");

        Feed feed = feedRepository.saveAndFlush(
                Feed.builder()
                        .authorId(authorId)
                        .weatherId(UUID.randomUUID())
                        .content("오늘의 코디")
                        .weatherSkyStatus("CLEAR")
                        .weatherPrecipitationType("NONE")
                        .build()
        );

        feedId = feed.getId();
    }

    @AfterEach
    void cleanUp() {
        // 이 클래스 전용 H2 테스트 DB의 데이터를 정리한다.
        feedLikeRepository.deleteAll();
        feedRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 사용자가 반복 요청해도 좋아요 기록과 개수는 1로 유지된다")
    void repeatedRequestsKeepOneLike() {
        feedLikeService.like(feedId, firstUserId);
        feedLikeService.like(feedId, firstUserId);

        assertThat(feedLikeRepository.findAll())
                .singleElement()
                .satisfies(like -> {
                    assertThat(like.getFeedId()).isEqualTo(feedId);
                    assertThat(like.getUserId()).isEqualTo(firstUserId);
                });

        assertThat(savedLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 요청해도 좋아요는 한 번만 반영된다")
    void concurrentRequestsFromSameUserKeepOneLike() throws Exception {
        likeConcurrently(List.of(firstUserId, firstUserId));

        assertThat(feedLikeRepository.findAll())
                .singleElement()
                .satisfies(like -> {
                    assertThat(like.getFeedId()).isEqualTo(feedId);
                    assertThat(like.getUserId()).isEqualTo(firstUserId);
                });

        assertThat(savedLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("다른 사용자가 동시에 요청하면 두 좋아요와 개수가 모두 반영된다")
    void concurrentRequestsFromDifferentUsersKeepBothLikes() throws Exception {
        likeConcurrently(List.of(firstUserId, secondUserId));

        assertThat(feedLikeRepository.findAll())
                .allSatisfy(like ->
                        assertThat(like.getFeedId()).isEqualTo(feedId)
                )
                .extracting(like -> like.getUserId())
                .containsExactlyInAnyOrder(firstUserId, secondUserId);

        assertThat(savedLikeCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("한 사용자가 좋아요를 두 번 취소해도 다른 사용자의 좋아요와 개수는 유지된다")
    void repeatedUnlikeKeepsOtherUsersLike() {
        feedLikeService.like(feedId, firstUserId);
        feedLikeService.like(feedId, secondUserId);

        feedLikeService.unlike(feedId, firstUserId);
        feedLikeService.unlike(feedId, firstUserId);

        assertThat(feedLikeRepository.findAll())
                .singleElement()
                .satisfies(like -> {
                    assertThat(like.getFeedId()).isEqualTo(feedId);
                    assertThat(like.getUserId()).isEqualTo(secondUserId);
                });
        assertThat(savedLikeCount()).isEqualTo(1L);
    }

    private void likeConcurrently(List<UUID> userIds) throws Exception {
        ExecutorService executor =
                Executors.newFixedThreadPool(userIds.size());

        CountDownLatch ready = new CountDownLatch(userIds.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        try {
            for (UUID userId : userIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
                    }

                    feedLikeService.like(feedId, userId);
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<Void> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 작업 종료 시간 초과");
            }
        }
    }

    private long savedLikeCount() {
        return feedRepository.findById(feedId)
                .orElseThrow()
                .getLikeCount();
    }

    private UUID createUser(String name) {
        return userRepository.saveAndFlush(
                User.create(
                        name,
                        UUID.randomUUID() + "@feed-like.test",
                        "encoded-password"
                )
        ).getId();
    }
}