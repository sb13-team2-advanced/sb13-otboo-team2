package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(2);
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(10);
    private static final Duration RESERVATION_DURATION = Duration.ofMinutes(5);

    private final WeatherLocationService weatherLocationService;
    private final WeatherForecastCollector weatherForecastCollector;
    private final WeatherService weatherService;
    private final WeatherLocationRepository weatherLocationRepository;
    private final Clock clock;

    public List<WeatherDto> getWeathers(
        double latitude,
        double longitude
    ) {
        WeatherLocation location =
            weatherLocationService.findOrCreate(latitude, longitude);

        if (location.getLastCollectedAt() == null) {
            Instant now = clock.instant();

            if (!location.getNextRefreshAt().isAfter(now)) {
                Instant reservedUntil = now.plus(RESERVATION_DURATION);

                int updatedCount =
                    weatherLocationRepository.reserveInitialCollection(
                        location.getId(),
                        now,
                        reservedUntil
                    );

                if (updatedCount == 1) {
                    LocalDate today = LocalDate.now(
                        clock.withZone(KOREA_ZONE)
                    );


                    try {
                        collectInitialWeather(location, today);
                    } catch (RuntimeException e) {
                        Instant failedAt = clock.instant();

                        location.scheduleRetry(
                            failedAt,
                            failedAt.plus(RETRY_INTERVAL)
                        );

                        weatherLocationRepository.save(location);

                        throw e;
                    }
                }
            }
        }

        return weatherService.getWeathers(latitude, longitude);
    }

    private void collectInitialWeather(
        WeatherLocation location,
        LocalDate today
    ) {

        weatherForecastCollector.collectForecasts(location);

        for (int day = 0; day < 5; day++) {
            weatherService.calculateAndSaveDailyWeather(
                location,
                today.plusDays(day)
            );
        }

        Instant completedAt = clock.instant();

        location.completeCollection(
            completedAt,
            completedAt.plus(REFRESH_INTERVAL)
        );

        weatherLocationRepository.save(location);
    }
}
