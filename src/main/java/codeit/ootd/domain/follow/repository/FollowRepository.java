package codeit.ootd.domain.follow.repository;

import codeit.ootd.domain.follow.entity.Follow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);
    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);
    long countByFollowerId(UUID followerId);
    long countByFolloweeId(UUID followeeId);

    @Query("""
        select f.followerId
        from Follow f
        where f.followeeId = :followeeId
        """)
    List<UUID> findFollowerIdsByFolloweeId(@Param("followeeId") UUID followeeId);

}
