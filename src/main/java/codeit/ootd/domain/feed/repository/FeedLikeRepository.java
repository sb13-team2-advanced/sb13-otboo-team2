package codeit.ootd.domain.feed.repository;

import codeit.ootd.domain.feed.entity.FeedLike;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedLikeRepository extends JpaRepository<FeedLike, UUID> {

    List<FeedLike> findAllByUserIdAndFeedIdIn(
            UUID userId,
            List<UUID> feedIds
    );
}