package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedComment;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedCommentService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-comment-create;DB_CLOSE_DELAY=-1"
})
@DisplayName("피드 댓글 등록 통합 테스트")
class FeedCommentServiceImplIntegrationTest {

    @Autowired
    private FeedCommentService feedCommentService;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedCommentRepository feedCommentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("다른 사용자의 피드에 댓글을 등록하면 댓글이 저장되고 댓글 수가 1 증가한다")
    void createsCommentAndIncreasesCommentCount() {
        UUID feedAuthorId = createUser("피드 작성자");
        UUID commentAuthorId = createUser("댓글 작성자");
        Feed feed = feedRepository.saveAndFlush(
                Feed.builder()
                        .authorId(feedAuthorId)
                        .weatherId(UUID.randomUUID())
                        .content("오늘의 코디")
                        .weatherSkyStatus("CLEAR")
                        .weatherPrecipitationType("NONE")
                        .build()
        );
        UUID feedId = feed.getId();
        int previousCount = feed.getCommentCount();
        String content = "색 조합이 잘 어울려요!";
        CommentCreateRequest request =
                new CommentCreateRequest(feedId, commentAuthorId, content);

        CommentDto result = feedCommentService.create(feedId, request, commentAuthorId);

        FeedComment saved = feedCommentRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getFeedId()).isEqualTo(feedId);
        assertThat(saved.getAuthorId()).isEqualTo(commentAuthorId);
        assertThat(saved.getContent()).isEqualTo(content);

        assertThat(result.feedId()).isEqualTo(feedId);
        assertThat(result.author().userId()).isEqualTo(commentAuthorId);
        assertThat(result.author().name()).isEqualTo("댓글 작성자");
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.createdAt()).isNotNull();

        Feed updatedFeed = feedRepository.findById(feedId).orElseThrow();
        assertThat(updatedFeed.getCommentCount()).isEqualTo(previousCount + 1);
    }

    private UUID createUser(String name) {
        return userRepository.saveAndFlush(
                User.create(name, UUID.randomUUID() + "@feed-comment.test", "encoded-password")
        ).getId();
    }
}