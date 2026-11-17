package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.domain.weather.service.WeatherForecastCollector;
import codeit.ootd.domain.weather.service.WeatherService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class WeatherBatchAggregationFailureIntegrationTest {

    private static final ZoneId KOREA_ZONE =
        ZoneId.of("Asia/Seoul");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ootd_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("weatherCollectionJob")
    private Job weatherCollectionJob;

    @Autowired
    private WeatherLocationRepository weatherLocationRepository;

    @Autowired
    private WeatherForecastRepository weatherForecastRepository;

    @Autowired
    private WeatherRepository weatherRepository;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private WeatherForecastCollector weatherForecastCollector;

    @MockitoBean
    private WeatherBatchRateLimiter weatherBatchRateLimiter;

    @MockitoSpyBean
    private WeatherService weatherService;

    @Test
    @DisplayName("3일 차 집계 실패 후 재시도하면 기존 날씨와 나머지 날짜를 정상 복구한다")
    void aggregationFailureRecoversOnNextRun() throws Exception {

        Instant startedAt = Instant.parse("2026-11-13T03:00:00Z");

        AtomicReference<Instant> currentTime =
            new AtomicReference<>(startedAt);

        when(clock.instant()).thenAnswer(
            invocation -> currentTime.get()
        );

        LocalDate today = LocalDate.ofInstant(
            startedAt,
            KOREA_ZONE
        );

        WeatherLocation location = new WeatherLocation(
            "test-" + UUID.randomUUID()
                .toString()
                .substring(0, 20),
            37.5665,
            126.9780,
            startedAt.minus(Duration.ofHours(1))
        );

        Instant previousCollectedAt =
            startedAt.minus(Duration.ofHours(3));

        location.completeCollection(
            previousCollectedAt,
            startedAt.minus(Duration.ofHours(1))
        );

        location = weatherLocationRepository.saveAndFlush(location);

        UUID locationId = location.getId();

        List<WeatherForecast> forecasts = new ArrayList<>();

        for (int day = 0; day < 5; day++) {
            forecasts.add(
                forecast(
                    locationId,
                    today.plusDays(day),
                    startedAt
                )
            );
        }

        weatherForecastRepository.saveAllAndFlush(forecasts);

        doThrow(new IllegalStateException("3일 차 집계 실패"))
            .doCallRealMethod()
            .when(weatherService)
            .calculateAndSaveDailyWeather(
                any(WeatherLocation.class),
                eq(today.plusDays(2))
            );

        JobExecution failedExecution = jobOperator.start(
            weatherCollectionJob,
            new JobParametersBuilder()
                .addLong("runSlot", startedAt.getEpochSecond() / 600)
                .toJobParameters()
        );

        assertThat(failedExecution.getStatus())
            .isEqualTo(BatchStatus.FAILED);

        List<Weather> partiallySaved = weatherRepository
            .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                locationId,
                today,
                today.plusDays(4)
            );

        assertThat(partiallySaved)
            .extracting(Weather::getWeatherDate)
            .containsExactly(
                today,
                today.plusDays(1)
            );

        UUID firstWeatherId = partiallySaved.get(0).getId();
        UUID secondWeatherId = partiallySaved.get(1).getId();

        assertThat(partiallySaved.get(0).getTemperatureAvg())
            .isEqualTo(18.0);

        WeatherLocation failedLocation = weatherLocationRepository
            .findById(locationId)
            .orElseThrow();

        assertThat(failedLocation.getLastCollectedAt())
            .isEqualTo(previousCollectedAt);

        assertThat(failedLocation.getNextRefreshAt())
            .isEqualTo(startedAt.plus(Duration.ofMinutes(10)));

        assertThat(weatherLocationRepository
            .findTop20ByResolutionAndLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                (short) 7,
                startedAt.plus(Duration.ofMinutes(9))
            ))
            .noneMatch(item -> item.getId().equals(locationId));

        Instant retryAt = startedAt.plus(Duration.ofMinutes(11));

        WeatherForecast updatedForecast = weatherForecastRepository
            .findByLocationIdAndForecastAt(
                locationId,
                today.atTime(12, 0)
                    .atZone(KOREA_ZONE)
                    .toInstant()
            )
            .orElseThrow();

        updatedForecast.updateForecast(
            retryAt,
            25.0,
            (short) 65,
            800,
            0.2,
            0.0,
            0.0,
            2.0
        );

        weatherForecastRepository.saveAndFlush(updatedForecast);

        currentTime.set(retryAt);

        assertThat(weatherLocationRepository
            .findTop20ByResolutionAndLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                (short) 7,
                retryAt
            ))
            .extracting(WeatherLocation::getId)
            .contains(locationId);

        JobExecution recoveredExecution = jobOperator.start(
            weatherCollectionJob,
            new JobParametersBuilder()
                .addLong("runSlot", retryAt.getEpochSecond() / 600)
                .toJobParameters()
        );

        assertThat(recoveredExecution.getStatus())
            .isEqualTo(BatchStatus.COMPLETED);

        List<Weather> recoveredWeathers = weatherRepository
            .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                locationId,
                today,
                today.plusDays(4)
            );

        assertThat(recoveredWeathers)
            .extracting(Weather::getWeatherDate)
            .containsExactly(
                today,
                today.plusDays(1),
                today.plusDays(2),
                today.plusDays(3),
                today.plusDays(4)
            );

        assertThat(recoveredWeathers.get(0).getId())
            .isEqualTo(firstWeatherId);

        assertThat(recoveredWeathers.get(1).getId())
            .isEqualTo(secondWeatherId);

        assertThat(recoveredWeathers.get(0).getTemperatureAvg())
            .isEqualTo(25.0);

        WeatherLocation recoveredLocation = weatherLocationRepository
            .findById(locationId)
            .orElseThrow();

        assertThat(recoveredLocation.getLastCollectedAt())
            .isEqualTo(retryAt);

        assertThat(recoveredLocation.getNextRefreshAt())
            .isEqualTo(retryAt.plus(Duration.ofHours(2)));

        verify(weatherForecastCollector, times(2))
            .collectForecasts(any(WeatherLocation.class));

        verify(weatherService, times(2))
            .calculateAndSaveDailyWeather(
                any(WeatherLocation.class),
                eq(today.plusDays(2))
            );
    }

    private WeatherForecast forecast(
        UUID locationId,
        LocalDate date,
        Instant collectedAt
    ) {
        return new WeatherForecast(
            locationId,
            date.atTime(12, 0)
                .atZone(KOREA_ZONE)
                .toInstant(),
            collectedAt,
            18.0,
            (short) 60,
            800,
            0.2,
            0.0,
            0.0,
            2.0
        );
    }
}
