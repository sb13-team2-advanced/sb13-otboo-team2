package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.response.CommentDtoCursorResponse;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedComment;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedCommentService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-comment-list;DB_CLOSE_DELAY=-1"
})
@DisplayName("피드 댓글 목록 조회 통합 테스트")
class FeedCommentListIntegrationTest {

    @Autowired
    private FeedCommentService service;

    @Autowired
    private FeedRepository feeds;

    @Autowired
    private FeedCommentRepository comments;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("작성 시각이 같은 댓글을 한 건씩 넘겨도 해당 피드의 댓글을 모두 한 번씩 조회한다")
    void pagesThroughCommentsWithSameTimestamp() {
        UUID authorId = users.saveAndFlush(User.create(
                "댓글 작성자", UUID.randomUUID() + "@feed.test", "encoded-password"
        )).getId();

        UUID feedId = createFeed(authorId);
        UUID otherFeedId = createFeed(authorId);
        UUID firstId = createComment(feedId, authorId, "첫 번째 댓글");
        UUID secondId = createComment(feedId, authorId, "두 번째 댓글");
        createComment(otherFeedId, authorId, "다른 피드의 댓글");

        Instant sameTime = Instant.parse("2026-11-10T06:00:00Z");
        jdbc.update("update feed_comments set created_at = ? where feed_id = ?",
                Timestamp.from(sameTime), feedId);

        CommentDtoCursorResponse first = service.list(feedId, null, null, 1);

        assertThat(first.data()).hasSize(1);
        assertThat(first.totalCount()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isEqualTo(sameTime.toString());
        assertThat(first.nextIdAfter()).isEqualTo(first.data().get(0).id());

        CommentDtoCursorResponse second =
                service.list(feedId, first.nextCursor(), first.nextIdAfter(), 1);

        assertThat(second.data()).hasSize(1);
        assertThat(second.totalCount()).isEqualTo(2);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
        assertThat(second.nextIdAfter()).isNull();
        assertThat(java.util.List.of(first.data().get(0).id(), second.data().get(0).id()))
                .containsExactlyInAnyOrder(firstId, secondId);
    }

    private UUID createFeed(UUID authorId) {
        return feeds.saveAndFlush(Feed.builder()
                .authorId(authorId)
                .weatherId(UUID.randomUUID())
                .content("오늘의 코디")
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .build()).getId();
    }

    private UUID createComment(UUID feedId, UUID authorId, String content) {
        return comments.saveAndFlush(new FeedComment(feedId, authorId, content)).getId();
    }
}