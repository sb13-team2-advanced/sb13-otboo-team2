package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.domain.notification.service.NotificationSseService;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.domain.weather.service.WeatherService;
import codeit.ootd.infra.weather.H3Service;
import codeit.ootd.infra.weather.OpenWeatherMapClient;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
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
class WeatherBatchNotificationConsistencyIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private WeatherLocationRepository weatherLocationRepository;

    @Autowired
    private WeatherForecastRepository weatherForecastRepository;

    @Autowired
    private WeatherRepository weatherRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private H3Service h3Service;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private OpenWeatherMapClient openWeatherMapClient;

    @MockitoBean
    private WeatherBatchRateLimiter weatherBatchRateLimiter;

    @MockitoBean
    private NotificationSseService notificationSseService;

    @MockitoSpyBean
    private WeatherService weatherService;

    @Test
    @DisplayName("집계 실패 후에도 알림은 유지되고 동일 예보 재수집 시 중복 알림이 생성되지 않는다")
    void aggregationFailureDoesNotDuplicateNotificationOnRetry()
        throws Exception {

        Instant startedAt =
            Instant.parse("2026-11-13T03:00:00Z");

        AtomicReference<Instant> currentTime =
            new AtomicReference<>(startedAt);

        when(clock.instant()).thenAnswer(
            invocation -> currentTime.get()
        );

        double latitude = 37.5665;
        double longitude = 126.9780;

        User user = userRepository.saveAndFlush(
            User.create(
                "날씨 알림 테스트",
                UUID.randomUUID() + "@example.com",
                "encoded-password"
            )
        );

        UUID userId = user.getId();

        Profile profile = new Profile(userId);
        profile.updateLocation(latitude, longitude);
        profileRepository.saveAndFlush(profile);

        WeatherLocation location = new WeatherLocation(
            h3Service.toCellId(latitude, longitude),
            latitude,
            longitude,
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

        LocalDate today = LocalDate.ofInstant(
            startedAt,
            KOREA_ZONE
        );

        List<WeatherForecast> forecasts = new ArrayList<>();

        for (int day = 0; day < 5; day++) {
            forecasts.add(
                forecast(
                    locationId,
                    today.plusDays(day)
                        .atTime(12, 0)
                        .atZone(KOREA_ZONE)
                        .toInstant(),
                    previousCollectedAt
                )
            );
        }

        Instant forecastAt = startedAt.plus(Duration.ofHours(3));

        forecasts.add(
            forecast(
                locationId,
                forecastAt,
                previousCollectedAt
            )
        );

        weatherForecastRepository.saveAllAndFlush(forecasts);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(
                new OwmForecastResponse.ForecastItem(
                    forecastAt.getEpochSecond(),
                    new OwmForecastResponse.Main(
                        26.0,
                        (short) 65
                    ),
                    List.of(
                        new OwmForecastResponse.WeatherCondition(500)
                    ),
                    0.8,
                    null,
                    null,
                    new OwmForecastResponse.Wind(2.5)
                )
            )
        );

        when(openWeatherMapClient.getForecast(latitude, longitude))
            .thenReturn(response);

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
                .addLong(
                    "runSlot",
                    startedAt.getEpochSecond() / 600
                )
                .toJobParameters()
        );

        assertThat(failedExecution.getStatus())
            .isEqualTo(BatchStatus.FAILED);

        List<Notification> firstNotifications =
            notificationsFor(userId);

        assertThat(firstNotifications).hasSize(1);

        Notification firstNotification = firstNotifications.get(0);

        assertThat(firstNotification.getTitle())
            .isEqualTo("날씨 예보가 달라졌어요!");

        assertThat(firstNotification.getContent())
            .contains("비가 올", "높아졌어요");

        UUID notificationId = firstNotification.getId();

        assertThat(weatherRepository
            .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                locationId,
                today,
                today.plusDays(4)
            ))
            .hasSize(2);

        WeatherForecast updatedForecast = weatherForecastRepository
            .findByLocationIdAndForecastAt(locationId, forecastAt)
            .orElseThrow();

        assertThat(updatedForecast.getTemperature())
            .isEqualTo(26.0);

        assertThat(updatedForecast.getWeatherCode())
            .isEqualTo(500);

        Instant retryAt = startedAt.plus(Duration.ofMinutes(11));

        currentTime.set(retryAt);

        JobExecution recoveredExecution = jobOperator.start(
            weatherCollectionJob,
            new JobParametersBuilder()
                .addLong(
                    "runSlot",
                    retryAt.getEpochSecond() / 600
                )
                .toJobParameters()
        );

        assertThat(recoveredExecution.getStatus())
            .isEqualTo(BatchStatus.COMPLETED);

        List<Notification> recoveredNotifications =
            notificationsFor(userId);

        assertThat(recoveredNotifications)
            .extracting(Notification::getId)
            .containsExactly(notificationId);

        assertThat(weatherRepository
            .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                locationId,
                today,
                today.plusDays(4)
            ))
            .hasSize(5);

        WeatherLocation recoveredLocation = weatherLocationRepository
            .findById(locationId)
            .orElseThrow();

        assertThat(recoveredLocation.getLastCollectedAt())
            .isEqualTo(retryAt);

        assertThat(recoveredLocation.getNextRefreshAt())
            .isEqualTo(retryAt.plus(Duration.ofHours(2)));

        verify(openWeatherMapClient, times(2))
            .getForecast(latitude, longitude);

        verify(notificationSseService, times(1))
            .send(
                eq(userId),
                any(NotificationDto.class)
            );
    }

    private WeatherForecast forecast(
        UUID locationId,
        Instant forecastAt,
        Instant collectedAt
    ) {
        return new WeatherForecast(
            locationId,
            forecastAt,
            collectedAt,
            20.0,
            (short) 65,
            800,
            0.2,
            0.0,
            0.0,
            2.5
        );
    }

    private List<Notification> notificationsFor(UUID userId) {
        return notificationRepository.findAll()
            .stream()
            .filter(notification ->
                notification.getReceiverId().equals(userId)
            )
            .toList();
    }
}
