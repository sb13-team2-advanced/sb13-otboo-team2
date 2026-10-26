package codeit.ootd.domain.weather.repository;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherForecastRepository
    extends JpaRepository<WeatherForecast, UUID> {

    Optional<WeatherForecast> findByLocationIdAndForecastAt(
        UUID locationId,
        Instant forecastAt
    );
}
