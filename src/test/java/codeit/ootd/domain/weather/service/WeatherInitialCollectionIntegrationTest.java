package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.infra.weather.OpenWeatherMapClient;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class WeatherInitialCollectionIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW =
        Instant.parse("2026-11-17T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ootd_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private WeatherQueryService weatherQueryService;

    @Autowired
    private WeatherLocationRepository locationRepository;

    @Autowired
    private WeatherForecastRepository forecastRepository;

    @Autowired
    private WeatherRepository weatherRepository;

    @MockitoBean
    private WeatherLocationService weatherLocationService;

    @MockitoBean
    private OpenWeatherMapClient openWeatherMapClient;

    @MockitoBean
    private Clock clock;

    private UUID locationId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.withZone(KOREA_ZONE))
            .thenReturn(Clock.fixed(NOW, KOREA_ZONE));
    }

    @AfterEach
    void tearDown() {
        if (locationId == null) {
            return;
        }

        weatherRepository.deleteAll(
            weatherRepository
                .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                    locationId,
                    TODAY.minusDays(1),
                    TODAY.plusDays(5)
                )
        );

        forecastRepository.deleteAll(
            forecastRepository.findForecastsForPeriod(
                locationId,
                TODAY.minusDays(1).atStartOfDay(KOREA_ZONE).toInstant(),
                TODAY.plusDays(6).atStartOfDay(KOREA_ZONE).toInstant()
            )
        );

        locationRepository.deleteById(locationId);
    }

    @Test
    @DisplayName("최초 수집 시 Forecast5와 One Call을 실제 DB에 저장하고 일별 날씨로 집계한다")
    void initialCollectionCombinesForecast5AndOneCall() {
        WeatherLocation location = prepareLocation();

        when(openWeatherMapClient.getForecast(LATITUDE, LONGITUDE))
            .thenReturn(forecastResponse());

        when(openWeatherMapClient.getTodayHourlyHistory(
            LATITUDE, LONGITUDE, NOW
        )).thenReturn(List.of(
            hourlyItem(7, 11.0, 0.1, 0.2),
            hourlyItem(8, 12.0, 0.2, 0.4),
            hourlyItem(9, 13.0, 0.3, 0.6)
        ));

        List<WeatherDto> result =
            weatherQueryService.getWeathers(LATITUDE, LONGITUDE);

        Instant historicalAt = TODAY.atTime(9, 0)
            .atZone(KOREA_ZONE)
            .toInstant();

        List<WeatherForecast> forecasts =
            forecastRepository.findForecastsForPeriod(
                locationId,
                TODAY.atStartOfDay(KOREA_ZONE).toInstant(),
                TODAY.plusDays(1).atStartOfDay(KOREA_ZONE).toInstant()
            );

        assertThat(forecasts).hasSize(3);

        WeatherForecast historical = forecastRepository
            .findByLocationIdAndForecastAt(locationId, historicalAt)
            .orElseThrow();

        assertThat(historical.getTemperature()).isEqualTo(13.0);
        assertThat(historical.getWeatherCode()).isEqualTo(500);
        assertThat(historical.getRainAmount())
            .isCloseTo(0.6, offset(0.000001));
        assertThat(historical.getPrecipitationProbability())
            .isEqualTo(0.6);

        Weather dailyWeather = weatherRepository
            .findByLocationIdAndWeatherDate(locationId, TODAY)
            .orElseThrow();

        assertThat(dailyWeather.getForecastSlotCount()).isEqualTo((short) 3);
        assertThat(dailyWeather.getTemperatureAvg())
            .isCloseTo(55.0 / 3, offset(0.000001));
        assertThat(dailyWeather.getTemperatureMin()).isEqualTo(13.0);
        assertThat(dailyWeather.getTemperatureMax()).isEqualTo(22.0);
        assertThat(dailyWeather.getRainAmount())
            .isCloseTo(0.6, offset(0.000001));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(dailyWeather.getId());
        assertThat(result.get(0).temperature().current())
            .isCloseTo(55.0 / 3, offset(0.000001));

        WeatherLocation completed = locationRepository
            .findById(locationId)
            .orElseThrow();

        assertThat(completed.getLastCollectedAt()).isEqualTo(NOW);
        assertThat(completed.getNextRefreshAt())
            .isEqualTo(NOW.plusSeconds(7200));
        assertThat(completed.isInitialCollectionInProgress()).isFalse();

        verify(openWeatherMapClient)
            .getTodayHourlyHistory(LATITUDE, LONGITUDE, NOW);
    }

    @Test
    @DisplayName("One Call 실패 시 Forecast5만 저장하고 최초 수집을 정상 완료한다")
    void initialCollectionContinuesWhenOneCallFails() {
        prepareLocation();

        when(openWeatherMapClient.getForecast(LATITUDE, LONGITUDE))
            .thenReturn(forecastResponse());

        when(openWeatherMapClient.getTodayHourlyHistory(
            LATITUDE, LONGITUDE, NOW
        )).thenThrow(new IllegalStateException("One Call 호출 실패"));

        List<WeatherDto> result =
            weatherQueryService.getWeathers(LATITUDE, LONGITUDE);

        List<WeatherForecast> forecasts =
            forecastRepository.findForecastsForPeriod(
                locationId,
                TODAY.atStartOfDay(KOREA_ZONE).toInstant(),
                TODAY.plusDays(1).atStartOfDay(KOREA_ZONE).toInstant()
            );

        assertThat(forecasts).hasSize(2);

        Weather dailyWeather = weatherRepository
            .findByLocationIdAndWeatherDate(locationId, TODAY)
            .orElseThrow();

        assertThat(dailyWeather.getForecastSlotCount()).isEqualTo((short) 2);
        assertThat(dailyWeather.getTemperatureAvg()).isEqualTo(21.0);
        assertThat(dailyWeather.getTemperatureMin()).isEqualTo(20.0);
        assertThat(dailyWeather.getTemperatureMax()).isEqualTo(22.0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(dailyWeather.getId());

        WeatherLocation completed = locationRepository
            .findById(locationId)
            .orElseThrow();

        assertThat(completed.getLastCollectedAt()).isEqualTo(NOW);
        assertThat(completed.getNextRefreshAt())
            .isEqualTo(NOW.plusSeconds(7200));
        assertThat(completed.isInitialCollectionInProgress()).isFalse();
    }

    private WeatherLocation prepareLocation() {
        WeatherLocation location = locationRepository.saveAndFlush(
            new WeatherLocation(
                "test-" + UUID.randomUUID().toString().substring(0, 20),
                LATITUDE,
                LONGITUDE,
                NOW.minusSeconds(60)
            )
        );

        locationId = location.getId();

        when(weatherLocationService.findOrCreate(LATITUDE, LONGITUDE))
            .thenReturn(location);

        when(weatherLocationService.getLocation(LATITUDE, LONGITUDE))
            .thenReturn(new WeatherAPILocation(
                LATITUDE,
                LONGITUDE,
                LATITUDE,
                LONGITUDE,
                List.of("서울특별시", "중구", "명동")
            ));

        return location;
    }

    private OwmForecastResponse forecastResponse() {
        return new OwmForecastResponse(
            List.of(
                forecastItem(NOW, 20.0),
                forecastItem(NOW.plusSeconds(10800), 22.0)
            )
        );
    }

    private OwmForecastResponse.ForecastItem forecastItem(
        Instant forecastAt,
        double temperature
    ) {
        return new OwmForecastResponse.ForecastItem(
            forecastAt.getEpochSecond(),
            new OwmForecastResponse.Main(temperature, (short) 60),
            List.of(new OwmForecastResponse.WeatherCondition(800)),
            0.2,
            null,
            null,
            new OwmForecastResponse.Wind(2.0)
        );
    }

    private OwmOneCallHourlyResponse.HourlyItem hourlyItem(
        int hour,
        double temperature,
        double rainAmount,
        double precipitationProbability
    ) {
        return new OwmOneCallHourlyResponse.HourlyItem(
            TODAY.atTime(hour, 0)
                .atZone(KOREA_ZONE)
                .toEpochSecond(),
            temperature,
            (short) 70,
            3.0,
            List.of(new OwmOneCallHourlyResponse.WeatherCondition(500)),
            precipitationProbability,
            new OwmOneCallHourlyResponse.Precipitation(rainAmount),
            null
        );
    }
}
