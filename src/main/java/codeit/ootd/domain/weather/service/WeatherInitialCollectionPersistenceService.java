package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.infra.weather.OwmHourlyForecastConverter.ThreeHourForecast;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeatherInitialCollectionPersistenceService {

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(2);

    private final WeatherLocationRepository weatherLocationRepository;
    private final WeatherForecastService weatherForecastService;
    private final WeatherService weatherService;
    private final Clock clock;

    @Transactional
    public void save(
        UUID locationId,
        UUID token,
        OwmForecastResponse forecastResponse,
        List<ThreeHourForecast> historicalForecasts,
        LocalDate today
    ) {
        WeatherLocation location = weatherLocationRepository
            .findByIdForUpdate(locationId)
            .orElseThrow();

        if (!location.isInitialCollectionInProgress()
            || !token.equals(location.getInitialCollectionToken())) {
            return;
        }

        weatherForecastService.saveForecasts(location, forecastResponse);
        weatherForecastService.saveHistoricalForecasts(
            location,
            historicalForecasts
        );

        for (int day = 0; day < 6; day++) {
            weatherService.calculateAndSaveDailyWeather(
                location,
                today.plusDays(day)
            );
        }

        Instant completedAt = clock.instant();

        int updated = weatherLocationRepository.completeInitialCollection(
            locationId,
            token,
            completedAt,
            completedAt.plus(REFRESH_INTERVAL)
        );

        if (updated != 1) {
            throw new IllegalStateException(
                "최초 수집 완료 상태를 저장하지 못했습니다."
            );
        }
    }
}
