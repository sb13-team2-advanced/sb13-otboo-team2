package codeit.ootd.domain.feed.repository;

import codeit.ootd.domain.feed.entity.FeedClothes;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedClothesRepository extends JpaRepository<FeedClothes, UUID> {
}