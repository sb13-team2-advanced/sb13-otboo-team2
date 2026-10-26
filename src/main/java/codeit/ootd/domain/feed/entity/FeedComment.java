package codeit.ootd.domain.feed.entity;

import codeit.ootd.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feed_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedComment extends BaseEntity {

    @Column(name = "feed_id", nullable = false)
    private UUID feedId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public FeedComment(UUID feedId, UUID authorId, String content) {
        this.feedId = feedId;
        this.authorId = authorId;
        this.content = content;
    }
}