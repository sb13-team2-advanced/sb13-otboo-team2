package codeit.ootd.domain.follow.repository;

import codeit.ootd.domain.follow.entity.Follow;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);
    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);
    long countByFollowerId(UUID followerId);
    long countByFolloweeId(UUID followeeId);
}
