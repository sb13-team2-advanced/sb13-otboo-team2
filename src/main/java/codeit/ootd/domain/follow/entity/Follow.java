package codeit.ootd.domain.follow.entity;

import codeit.ootd.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "follows", uniqueConstraints = {
    @UniqueConstraint(name = "uk_follows_follower_followee", columnNames = {
        "follower_id", "followee_id"
    })
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseEntity {

    @Column(name = "follower_id", nullable = false, updatable = false)
    private UUID followerId;

    @Column(name = "followee_id", nullable = false, updatable = false)
    private UUID followeeId;

    public Follow(UUID followerId, UUID followeeId) {
        this.followerId = Objects.requireNonNull(followerId, "followerId must not be null");
        this.followeeId = Objects.requireNonNull(followeeId, "followeeId must not be null");
    }
}
