package codeit.ootd.domain.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.follow.entity.Follow;
import codeit.ootd.domain.follow.repository.FollowRepository;
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
        "spring.datasource.url=jdbc:h2:mem:feed-follower-lookup-test;DB_CLOSE_DELAY=-1"
})
@Transactional
@DisplayName("새 피드 알림 대상 조회 테스트")
class FeedFollowerLookupTest {

    @Autowired
    private FollowRepository followRepository;

    @Test
    @DisplayName("작성자를 팔로우하는 사용자만 알림 대상으로 조회한다")
    void findsOnlyFollowersOfAuthor() {
        UUID authorId = UUID.randomUUID();
        UUID firstFollowerId = UUID.randomUUID();
        UUID secondFollowerId = UUID.randomUUID();
        UUID followedByAuthorId = UUID.randomUUID();
        UUID unrelatedUserId = UUID.randomUUID();

        followRepository.saveAllAndFlush(List.of(
                new Follow(firstFollowerId, authorId),
                new Follow(secondFollowerId, authorId),
                new Follow(authorId, followedByAuthorId),
                new Follow(unrelatedUserId, followedByAuthorId)
        ));

        List<UUID> result =
                followRepository.findFollowerIdsByFolloweeId(authorId);

        assertThat(result)
                .containsExactlyInAnyOrder(firstFollowerId, secondFollowerId);
    }

    @Test
    @DisplayName("작성자의 팔로워가 없으면 빈 목록을 반환한다")
    void returnsEmptyWhenAuthorHasNoFollowers() {
        UUID authorId = UUID.randomUUID();

        followRepository.saveAndFlush(
                new Follow(authorId, UUID.randomUUID())
        );

        List<UUID> result =
                followRepository.findFollowerIdsByFolloweeId(authorId);

        assertThat(result).isEmpty();
    }
}