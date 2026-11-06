package codeit.ootd.domain.feed.repository;

import codeit.ootd.domain.feed.entity.FeedLike;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedLikeRepository extends JpaRepository<FeedLike, UUID> {

    boolean existsByFeedIdAndUserId(UUID feedId, UUID userId);

    List<FeedLike> findAllByUserIdAndFeedIdIn(
            UUID userId,
            List<UUID> feedIds
    );

    @Modifying
    @Query("delete from FeedLike fl where fl.feedId = :feedId")
    void deleteAllByFeedId(@Param("feedId") UUID feedId);
}