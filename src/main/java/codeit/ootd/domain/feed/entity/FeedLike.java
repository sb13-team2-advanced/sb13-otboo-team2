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
@Table(name = "feed_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedLike extends BaseEntity {

    @Column(name = "feed_id", nullable = false)
    private UUID feedId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public FeedLike(UUID feedId, UUID userId) {
        this.feedId = feedId;
        this.userId = userId;
    }
}