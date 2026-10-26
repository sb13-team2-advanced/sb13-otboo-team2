package codeit.ootd.domain.weather.repository;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherForecastRepository
    extends JpaRepository<WeatherForecast, UUID> {

    Optional<WeatherForecast> findByLocationIdAndForecastAt(
        UUID locationId,
        Instant forecastAt
    );

    @Query("""
        SELECT f FROM WeatherForecast f
        WHERE f.locationId = :locationId
          AND f.locationId >= :start
          AND f.forecastAt < :end
        """)
    List<WeatherForecast> findForecastsForPeriod(
        @Param("locationId") UUID locationId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );
}
