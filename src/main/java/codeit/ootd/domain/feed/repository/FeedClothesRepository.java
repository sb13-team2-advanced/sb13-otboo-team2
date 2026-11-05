package codeit.ootd.domain.feed.repository;

import codeit.ootd.domain.feed.entity.FeedClothes;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedClothesRepository extends JpaRepository<FeedClothes, UUID> {

    List<FeedClothes> findAllByFeedIdIn(List<UUID> feedIds);
}