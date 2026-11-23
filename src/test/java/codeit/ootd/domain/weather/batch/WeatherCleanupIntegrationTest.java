package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class WeatherCleanupIntegrationTest {

    /**
     * pgvector가 포함된 PostgreSQL 16에서 통합 테스트와 Flyway 마이그레이션을 실행한다.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg16-bookworm")
                .asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("ootd_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("weatherCleanupJob")
    private Job weatherCleanupJob;

    @Autowired
    private WeatherLocationRepository locationRepository;

    @Autowired
    private WeatherForecastRepository forecastRepository;

    @Autowired
    private WeatherRepository weatherRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @MockitoSpyBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    private UUID createdLocationId;
    private UUID createdUserId;
    private UUID createdFeedId;
    private final List<LocalDate> createdUsageDates = new ArrayList<>();

    @AfterEach
    void cleanupTestData() {
        JdbcTemplate cleanup = new JdbcTemplate(dataSource);

        if (createdFeedId != null) {
            cleanup.update(
                "DELETE FROM feeds WHERE id = ?",
                createdFeedId
            );
        }

        if (createdLocationId != null) {
            cleanup.update(
                "DELETE FROM weather_forecast WHERE location_id = ?",
                createdLocationId
            );

            cleanup.update(
                "DELETE FROM weather WHERE location_id = ?",
                createdLocationId
            );

            cleanup.update(
                "DELETE FROM weather_location WHERE id = ?",
                createdLocationId
            );
        }

        if (createdUserId != null) {
            cleanup.update(
                "DELETE FROM users WHERE id = ?",
                createdUserId
            );
        }

        for (LocalDate usageDate : createdUsageDates) {
            cleanup.update(
                "DELETE FROM weather_one_call_usage WHERE usage_date = ?",
                Date.valueOf(usageDate)
            );
        }
    }

    @Test
    @DisplayName("보관 기간이 지난 데이터만 삭제하고 피드 참조 날씨와 경계 날짜 데이터는 유지한다")
    void cleanupDeletesOnlyExpiredUnreferencedData() throws Exception {
        Instant now = Instant.parse("2026-11-16T03:00:00Z");
        when(clock.instant()).thenReturn(now);

        WeatherLocation location = saveLocation(now);
        UUID locationId = location.getId();

        WeatherForecast expiredForecast = saveForecast(
            locationId,
            Instant.parse("2026-11-09T02:59:59Z"),
            now
        );

        WeatherForecast boundaryForecast = saveForecast(
            locationId,
            Instant.parse("2026-11-09T03:00:00Z"),
            now
        );

        Weather expiredWeather = saveWeather(
            locationId,
            LocalDate.of(2026, 10, 15),
            now
        );

        Weather referencedWeather = saveWeather(
            locationId,
            LocalDate.of(2026, 10, 16),
            now
        );

        Weather boundaryWeather = saveWeather(
            locationId,
            LocalDate.of(2026, 10, 17),
            now
        );

        User user = userRepository.saveAndFlush(
            User.create(
                "테스트 사용자",
                UUID.randomUUID() + "@test.com",
                "encoded-password"
            )
        );

        createdUserId = user.getId();
        createdFeedId = UUID.randomUUID();

        JdbcTemplate sql = new JdbcTemplate(dataSource);

        sql.update("""
            INSERT INTO feeds (
                id, author_id, weather_id, content,
                created_at, updated_at,
                weather_sky_status,
                weather_precipitation_type,
                weather_precipitation_amount,
                weather_precipitation_probability,
                weather_temperature_current,
                weather_temperature_min,
                weather_temperature_max
            ) VALUES (
                ?, ?, ?, '테스트 피드',
                ?, ?, 'CLEAR', 'NONE',
                0, 0, 18, 16, 20
            )
            """,
            createdFeedId,
            user.getId(),
            referencedWeather.getId(),
            Timestamp.from(now),
            Timestamp.from(now)
        );

        LocalDate expiredUsageDate = LocalDate.of(2026, 11, 8);
        LocalDate boundaryUsageDate = LocalDate.of(2026, 11, 9);

        createdUsageDates.add(expiredUsageDate);
        createdUsageDates.add(boundaryUsageDate);

        sql.update("""
            INSERT INTO weather_one_call_usage (
                usage_date, request_count
            ) VALUES (?, ?), (?, ?)
            """,
            Date.valueOf(expiredUsageDate), 10,
            Date.valueOf(boundaryUsageDate), 20
        );

        JobExecution execution = jobOperator.start(
            weatherCleanupJob,
            new JobParametersBuilder()
                .addString("runDate", "2026-11-16")
                .toJobParameters()
        );

        assertThat(execution.getStatus())
            .isEqualTo(BatchStatus.COMPLETED);

        assertThat(forecastRepository.existsById(expiredForecast.getId()))
            .isFalse();

        assertThat(forecastRepository.existsById(boundaryForecast.getId()))
            .isTrue();

        assertThat(weatherRepository.existsById(expiredWeather.getId()))
            .isFalse();

        assertThat(weatherRepository.existsById(referencedWeather.getId()))
            .isTrue();

        assertThat(weatherRepository.existsById(boundaryWeather.getId()))
            .isTrue();

        assertThat(usageCount(expiredUsageDate)).isZero();
        assertThat(usageCount(boundaryUsageDate)).isEqualTo(1);

        assertThat(locationRepository.existsById(locationId))
            .isTrue();

        assertThat(sql.queryForObject(
            "SELECT COUNT(*) FROM feeds WHERE id = ?",
            Integer.class,
            createdFeedId
        )).isEqualTo(1);

        assertThat(execution.getStepExecutions())
            .singleElement()
            .satisfies(step ->
                assertThat(step.getWriteCount()).isEqualTo(3)
            );
    }

    @Test
    @DisplayName("같은 날짜에 완료된 날씨 정리 Job은 중복 실행되지 않는다")
    void completedCleanupJobDoesNotRunTwice() throws Exception {
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        when(clock.instant()).thenReturn(now);

        var parameters = new JobParametersBuilder()
            .addString("runDate", "2026-11-17")
            .toJobParameters();

        JobExecution first = jobOperator.start(
            weatherCleanupJob,
            parameters
        );

        assertThat(first.getStatus())
            .isEqualTo(BatchStatus.COMPLETED);

        assertThatThrownBy(
            () -> jobOperator.start(weatherCleanupJob, parameters)
        )
            .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    @Test
    @DisplayName("마지막 삭제 작업이 실패하면 앞서 삭제한 데이터도 롤백된다")
    void cleanupFailureRollsBackPreviousDeletions() throws Exception {
        Instant now = Instant.parse("2026-11-18T03:00:00Z");
        when(clock.instant()).thenReturn(now);

        WeatherLocation location = saveLocation(now);

        WeatherForecast expiredForecast = saveForecast(
            location.getId(),
            now.minusSeconds(8 * 24 * 60 * 60),
            now
        );

        Weather expiredWeather = saveWeather(
            location.getId(),
            LocalDate.of(2026, 10, 15),
            now
        );

        LocalDate usageDate = LocalDate.of(2026, 11, 1);
        createdUsageDates.add(usageDate);

        JdbcTemplate sql = new JdbcTemplate(dataSource);

        sql.update("""
            INSERT INTO weather_one_call_usage (
                usage_date, request_count
            ) VALUES (?, ?)
            """,
            Date.valueOf(usageDate),
            10
        );

        doThrow(new IllegalStateException("테스트용 삭제 실패"))
            .when(jdbcTemplate)
            .update(
                contains("DELETE FROM weather_one_call_usage"),
                any(Object[].class)
            );

        JobExecution execution = jobOperator.start(
            weatherCleanupJob,
            new JobParametersBuilder()
                .addString("runDate", "2026-11-18")
                .toJobParameters()
        );

        assertThat(execution.getStatus())
            .isEqualTo(BatchStatus.FAILED);

        assertThat(forecastRepository.existsById(expiredForecast.getId()))
            .isTrue();

        assertThat(weatherRepository.existsById(expiredWeather.getId()))
            .isTrue();

        assertThat(usageCount(usageDate))
            .isEqualTo(1);
    }

    private WeatherLocation saveLocation(Instant now) {
        WeatherLocation location = locationRepository.saveAndFlush(
            new WeatherLocation(
                "cleanup-" + UUID.randomUUID().toString().substring(0, 20),
                37.5665,
                126.9780,
                now.plusSeconds(3600)
            )
        );

        createdLocationId = location.getId();
        return location;
    }

    private WeatherForecast saveForecast(
        UUID locationId,
        Instant forecastAt,
        Instant collectedAt
    ) {
        return forecastRepository.saveAndFlush(
            new WeatherForecast(
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
            )
        );
    }

    private Weather saveWeather(
        UUID locationId,
        LocalDate weatherDate,
        Instant calculatedAt
    ) {
        return weatherRepository.saveAndFlush(
            new Weather(
                locationId,
                weatherDate,
                calculatedAt,
                18.0,
                16.0,
                20.0,
                (short) 60,
                800,
                0.2,
                0.0,
                0.0,
                2.0,
                (short) 8
            )
        );
    }

    private int usageCount(LocalDate date) {
        return new JdbcTemplate(dataSource).queryForObject(
            """
            SELECT COUNT(*)
            FROM weather_one_call_usage
            WHERE usage_date = ?
            """,
            Integer.class,
            Date.valueOf(date)
        );
    }
}
