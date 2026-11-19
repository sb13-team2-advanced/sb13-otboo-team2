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
          AND f.forecastAt >= :start
          AND f.forecastAt < :end
        ORDER BY f.forecastAt ASC
        """)
    List<WeatherForecast> findForecastsForPeriod(
        @Param("locationId") UUID locationId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    /**
     * 추천 시작 시각과 마지막 강수 구간의 종료점인 자정을 포함해 예보를 조회한다.
     */
    @Query("""
        SELECT f FROM WeatherForecast f
        WHERE f.locationId = :locationId
          AND f.forecastAt >= :start
          AND f.forecastAt <= :end
        ORDER BY f.forecastAt ASC
        """)
    List<WeatherForecast> findForecastsForRecommendation(
        @Param("locationId") UUID locationId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    List<WeatherForecast> findByLocationIdAndForecastAtIn(
        UUID locationId,
        List<Instant> forecastAts
    );
}
