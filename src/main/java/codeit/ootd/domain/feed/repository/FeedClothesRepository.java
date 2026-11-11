package codeit.ootd.domain.feed.repository;

import codeit.ootd.domain.feed.entity.FeedClothes;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedClothesRepository extends JpaRepository<FeedClothes, UUID> {

    List<FeedClothes> findAllByFeedIdIn(List<UUID> feedIds);

    @Modifying
    @Query("delete from FeedClothes fc where fc.feedId = :feedId")
    void deleteAllByFeedId(@Param("feedId") UUID feedId);
}