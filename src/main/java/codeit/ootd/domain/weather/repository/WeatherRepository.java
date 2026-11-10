package codeit.ootd.domain.weather.repository;

import codeit.ootd.domain.weather.entity.Weather;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherRepository
    extends JpaRepository<Weather, UUID> {

    Optional<Weather> findByLocationIdAndWeatherDate(
        UUID locationId,
        LocalDate weatherDate
    );

    List<Weather> findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
        UUID locationId,
        LocalDate startDate,
        LocalDate endDate
    );
}
