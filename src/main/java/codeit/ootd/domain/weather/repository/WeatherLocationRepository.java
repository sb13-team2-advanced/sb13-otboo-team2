package codeit.ootd.domain.weather.repository;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WeatherLocationRepository
    extends JpaRepository<WeatherLocation, UUID> {

    Optional<WeatherLocation> findByCellId(String cellId);

    List<WeatherLocation> findByNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(Instant now);

    List<WeatherLocation> findTop20ByNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(Instant now);

    @Modifying
    @Transactional
    @Query("""
        UPDATE WeatherLocation w
        SET w.nextRefreshAt = :reservedUntil
        WHERE w.id = :locationId
          AND w.lastCollectedAt IS NULL 
          AND w.nextRefreshAt <= :now
        """)
    int reserveInitialCollection(
        @Param("locationId") UUID locationId,
        @Param("now") Instant now,
        @Param("reservedUntil") Instant reservedUntil
    );
}
