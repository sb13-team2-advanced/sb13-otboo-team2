package codeit.ootd.domain.feed.repository;

import codeit.ootd.domain.feed.entity.Feed;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Feed f where f.id = :feedId")
    Optional<Feed> findByIdForUpdate(@Param("feedId") UUID feedId);
}