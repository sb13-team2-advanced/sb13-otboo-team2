package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.domain.weather.service.WeatherForecastCollector;
import codeit.ootd.domain.weather.service.WeatherLocationService;
import codeit.ootd.domain.weather.service.WeatherQueryService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class WeatherBatchIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

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

    @Autowired
    private WeatherQueryService weatherQueryService;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private WeatherForecastCollector weatherForecastCollector;

    @MockitoBean
    private WeatherBatchRateLimiter weatherBatchRateLimiter;

    @MockitoBean
    private WeatherLocationService weatherLocationService;

    @Test
    @DisplayName("23시에 여섯 번째 날짜까지 집계하면 자정 이후에도 날씨 5일치를 반환한다")
    void collectedAtElevenPmReturnsFiveDaysAfterMidnight() throws Exception {
        Instant collectedAt = Instant.parse("2026-11-13T14:00:00Z");
        AtomicReference<Instant> currentTime = new AtomicReference<>(collectedAt);

        when(clock.instant()).thenAnswer(invocation -> currentTime.get());
        when(clock.withZone(KOREA_ZONE)).thenAnswer(
            invocation -> Clock.fixed(currentTime.get(), KOREA_ZONE)
        );

        double latitude = 37.5665;
        double longitude = 126.9780;
        LocalDate nextDay = LocalDate.of(2026, 11, 14);

        WeatherLocation location = new WeatherLocation(
            "test-" + UUID.randomUUID().toString().substring(0, 20),
            latitude,
            longitude,
            collectedAt.minus(Duration.ofHours(1))
        );

        location.completeCollection(
            collectedAt.minus(Duration.ofHours(3)),
            collectedAt.minus(Duration.ofHours(1))
        );

        location = weatherLocationRepository.saveAndFlush(location);
        UUID locationId = location.getId();

        List<WeatherForecast> forecasts = new ArrayList<>();

        for (int day = 0; day < 5; day++) {
            for (int hour = 0; hour < 24; hour += 3) {
                Instant forecastAt = nextDay.plusDays(day)
                    .atTime(hour, 0)
                    .atZone(KOREA_ZONE)
                    .toInstant();

                forecasts.add(new WeatherForecast(
                    locationId,
                    forecastAt,
                    collectedAt,
                    18.0,
                    (short) 60,
                    800,
                    0.2,
                    0.0,
                    0.0,
                    2.0
                ));
            }
        }

        assertThat(forecasts).hasSize(40);
        weatherForecastRepository.saveAllAndFlush(forecasts);

        JobExecution execution = jobOperator.start(
            weatherCollectionJob,
            new JobParametersBuilder()
                .addLong("runSlot", collectedAt.getEpochSecond() / 600)
                .toJobParameters()
        );

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<Weather> saved = weatherRepository
            .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                locationId,
                nextDay,
                nextDay.plusDays(4)
            );

        assertThat(saved)
            .extracting(Weather::getWeatherDate)
            .containsExactly(
                nextDay,
                nextDay.plusDays(1),
                nextDay.plusDays(2),
                nextDay.plusDays(3),
                nextDay.plusDays(4)
            );

        currentTime.set(Instant.parse("2026-11-13T15:01:00Z"));

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(weatherLocationService.getLocation(latitude, longitude))
            .thenReturn(new WeatherAPILocation(
                latitude,
                longitude,
                latitude,
                longitude,
                List.of("서울특별시", "중구", "명동")
            ));

        List<WeatherDto> result = weatherQueryService.getWeathers(
            latitude,
            longitude
        );

        assertThat(result).hasSize(5);
        assertThat(result)
            .extracting(WeatherDto::id)
            .containsExactlyElementsOf(
                saved.stream().map(Weather::getId).toList()
            );
    }
}
