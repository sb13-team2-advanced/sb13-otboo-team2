package codeit.ootd.domain.feed.repository;

import codeit.ootd.domain.feed.entity.FeedComment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedCommentRepository extends JpaRepository<FeedComment, UUID> {

    List<FeedComment> findByFeedIdOrderByCreatedAtDescIdDesc(
            UUID feedId,
            Pageable pageable
    );

    @Query("""
            select fc from FeedComment fc
            where fc.feedId = :feedId
              and (
                  fc.createdAt < :cursor
                  or (fc.createdAt = :cursor and fc.id < :idAfter)
              )
            order by fc.createdAt desc, fc.id desc
            """)
    List<FeedComment> findNextPage(
            @Param("feedId") UUID feedId,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    long countByFeedId(UUID feedId);

    @Modifying
    @Query("delete from FeedComment fc where fc.feedId = :feedId")
    void deleteAllByFeedId(@Param("feedId") UUID feedId);
}