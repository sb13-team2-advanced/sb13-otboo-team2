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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(2);
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(10);
    private static final Duration RESERVATION_DURATION = Duration.ofMinutes(5);
    private static final long[] WAIT_INTERVALS_MS = {500, 500, 1000, 1000};

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
                UUID token = UUID.randomUUID();

                int updatedCount =
                    weatherLocationRepository.reserveInitialCollection(
                        location.getId(),
                        now,
                        reservedUntil,
                        token
                    );

                if (updatedCount == 1) {
                    LocalDate today = LocalDate.now(
                        clock.withZone(KOREA_ZONE)
                    );


                    try {
                        collectInitialWeather(location, today, token);
                    } catch (RuntimeException e) {
                        Instant failedAt = clock.instant();

                        weatherLocationRepository.scheduleInitialCollectionRetry(
                            location.getId(),
                            token,
                            failedAt.plus(RETRY_INTERVAL)
                        );

                        throw e;
                    }
                } else {
                    waitForInitialCollection(location.getId());
                }
            } else if (location.isInitialCollectionInProgress()) {
                waitForInitialCollection(location.getId());
            }
        }

        return weatherService.getWeathers(latitude, longitude);
    }

    private void waitForInitialCollection(UUID locationId) {
        for (long waitMillis : WAIT_INTERVALS_MS) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "날씨 최초 수집 대기 중 중단되었습니다.", e
                );
            }

            WeatherLocation latest = weatherLocationRepository
                .findById(locationId)
                .orElse(null);

            if (latest == null || latest.getLastCollectedAt() != null) {
                return;
            }

            if (!latest.isInitialCollectionInProgress()) {
                return;
            }
        }
    }

    private void collectInitialWeather(
        WeatherLocation location,
        LocalDate today,
        UUID token
    ) {

        weatherForecastCollector.collectForecasts(location);

        for (int day = 0; day < 5; day++) {
            weatherService.calculateAndSaveDailyWeather(
                location,
                today.plusDays(day)
            );
        }

        Instant completedAt = clock.instant();

        weatherLocationRepository.completeInitialCollection(
            location.getId(),
            token,
            completedAt,
            completedAt.plus(REFRESH_INTERVAL)
        );
    }
}
