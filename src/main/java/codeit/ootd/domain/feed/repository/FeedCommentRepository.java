package codeit.ootd.domain.feed.repository;

import codeit.ootd.domain.feed.entity.FeedComment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedCommentRepository extends JpaRepository<FeedComment, UUID> {

    @Modifying
    @Query("delete from FeedComment fc where fc.feedId = :feedId")
    void deleteAllByFeedId(@Param("feedId") UUID feedId);
}