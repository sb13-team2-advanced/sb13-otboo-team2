package codeit.ootd.domain.weather.repository;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherLocationRepository
    extends JpaRepository<WeatherLocation, UUID> {

    Optional<WeatherLocation> findByCellId(String cellId);

    List<WeatherLocation> findByNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(Instant now);

    List<WeatherLocation> findTop20ByNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(Instant now);

}
