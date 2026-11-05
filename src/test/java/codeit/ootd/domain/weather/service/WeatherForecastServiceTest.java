package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.service.WeatherChangeDetector.PrecipitationType;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherForecastServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-11-05T00:00:00Z");

    @Mock
    private WeatherForecastRepository weatherForecastRepository;

    @Mock
    private Clock clock;

    @Spy
    private WeatherChangeDetector weatherChangeDetector =
        new WeatherChangeDetector();

    @Spy
    private WeatherNotificationMessageBuilder weatherNotificationMessageBuilder =
        new WeatherNotificationMessageBuilder();

    @InjectMocks
    private WeatherForecastService weatherForecastService;

    @Test
    @DisplayName("기존 예보가 없으면 새로운 예보를 저장하고 알림을 생성하지 않는다")
    void saveForecastsCreatesNewForecast() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.plusSeconds(3600);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(forecastItem(forecastAt, 18.5, 800))
        );

        when(weatherForecastRepository.findByLocationIdAndForecastAt(
            locationId, forecastAt
        )).thenReturn(Optional.empty());

        when(clock.instant()).thenReturn(NOW);

        weatherForecastService.saveForecasts(location, response);

        ArgumentCaptor<WeatherForecast> captor =
            ArgumentCaptor.forClass(WeatherForecast.class);

        verify(weatherForecastRepository).save(captor.capture());

        WeatherForecast savedForecast = captor.getValue();

        assertThat(savedForecast.getLocationId()).isEqualTo(locationId);
        assertThat(savedForecast.getForecastAt()).isEqualTo(forecastAt);
        assertThat(savedForecast.getTemperature()).isEqualTo(18.5);
        assertThat(savedForecast.getHumidity()).isEqualTo((short) 65);
        assertThat(savedForecast.getWeatherCode()).isEqualTo(800);
        assertThat(savedForecast.getPrecipitationProbability()).isEqualTo(0.2);
        assertThat(savedForecast.getRainAmount()).isZero();
        assertThat(savedForecast.getSnowAmount()).isZero();
        assertThat(savedForecast.getWindSpeed()).isEqualTo(2.5);

        verifyNoInteractions(weatherNotificationMessageBuilder);
    }

    @Test
    @DisplayName("기존 예보가 있으면 최신 예보로 갱신한다")
    void saveForecastsUpdatesExistingForecast() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.plusSeconds(3600);
        Instant oldCollectedAt = NOW.minusSeconds(86400);

        WeatherForecast existingForecast = new WeatherForecast(
            locationId,
            forecastAt,
            oldCollectedAt,
            18.5,
            (short) 65,
            800,
            0.2,
            0.0,
            0.0,
            2.5
        );

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(new OwmForecastResponse.ForecastItem(
                forecastAt.getEpochSecond(),
                new OwmForecastResponse.Main(15.0, (short) 75),
                List.of(new OwmForecastResponse.WeatherCondition(500)),
                0.8,
                new OwmForecastResponse.Precipitation(1.3),
                null,
                new OwmForecastResponse.Wind(3.5)
            ))
        );

        when(weatherForecastRepository.findByLocationIdAndForecastAt(
            locationId, forecastAt
        )).thenReturn(Optional.of(existingForecast));

        when(clock.instant()).thenReturn(NOW);

        weatherForecastService.saveForecasts(location, response);

        verify(weatherForecastRepository).save(existingForecast);

        assertThat(existingForecast.getTemperature()).isEqualTo(15.0);
        assertThat(existingForecast.getHumidity()).isEqualTo((short) 75);
        assertThat(existingForecast.getWeatherCode()).isEqualTo(500);
        assertThat(existingForecast.getPrecipitationProbability()).isEqualTo(0.8);
        assertThat(existingForecast.getRainAmount()).isEqualTo(1.3);
        assertThat(existingForecast.getCollectedAt()).isAfter(oldCollectedAt);
    }

    @ParameterizedTest
    @DisplayName("수집 시각 이후 6시간 이내의 예보 변화만 알림 대상으로 판별한다")
    @CsvSource({
        "-1, false",
        "0, false",
        "1, true",
        "21600, true",
        "21601, false"
    })
    void saveForecastsDetectsChangesWithinSixHours(
        long secondsAfterCollection,
        boolean shouldNotify
    ) {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.plusSeconds(secondsAfterCollection);

        WeatherForecast existingForecast =
            previousForecast(locationId, forecastAt, 20.0, 800);

        when(weatherForecastRepository.findByLocationIdAndForecastAt(
            locationId, forecastAt
        )).thenReturn(Optional.of(existingForecast));

        when(clock.instant()).thenReturn(NOW);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(forecastItem(forecastAt, 26.0, 800))
        );

        weatherForecastService.saveForecasts(location, response);

        verify(weatherForecastRepository).save(existingForecast);

        if (shouldNotify) {
            verify(weatherNotificationMessageBuilder).build(
                true,
                false,
                Set.of()
            );
        } else {
            verifyNoInteractions(weatherNotificationMessageBuilder);
        }
    }

    @Test
    @DisplayName("여러 시간대의 예보 변화는 하나의 알림 메시지로 병합한다")
    void saveForecastsMergesChangesIntoSingleMessage() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant firstAt = NOW.plusSeconds(3600);
        Instant secondAt = NOW.plusSeconds(7200);
        Instant thirdAt = NOW.plusSeconds(10800);

        WeatherForecast first =
            previousForecast(locationId, firstAt, 20.0, 800);
        WeatherForecast second =
            previousForecast(locationId, secondAt, 20.0, 800);
        WeatherForecast third =
            previousForecast(locationId, thirdAt, 20.0, 800);

        when(weatherForecastRepository.findByLocationIdAndForecastAt(
            locationId, firstAt
        )).thenReturn(Optional.of(first));

        when(weatherForecastRepository.findByLocationIdAndForecastAt(
            locationId, secondAt
        )).thenReturn(Optional.of(second));

        when(weatherForecastRepository.findByLocationIdAndForecastAt(
            locationId, thirdAt
        )).thenReturn(Optional.of(third));

        when(clock.instant()).thenReturn(NOW);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(
                forecastItem(thirdAt, 14.0, 600),
                forecastItem(firstAt, 26.0, 500),
                forecastItem(secondAt, 25.0, 500)
            )
        );

        weatherForecastService.saveForecasts(location, response);

        verify(weatherForecastRepository).save(first);
        verify(weatherForecastRepository).save(second);
        verify(weatherForecastRepository).save(third);

        verify(weatherNotificationMessageBuilder).build(
            true,
            true,
            Set.of(
                PrecipitationType.RAIN,
                PrecipitationType.SNOW
            )
        );
    }

    @Test
    @DisplayName("기존 예보와 날씨 변화가 없으면 알림 메시지를 생성하지 않는다")
    void saveForecastsDoesNotNotifyWhenWeatherIsUnchanged() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.plusSeconds(3600);

        WeatherForecast existingForecast =
            previousForecast(locationId, forecastAt, 20.0, 800);

        when(weatherForecastRepository.findByLocationIdAndForecastAt(
            locationId, forecastAt
        )).thenReturn(Optional.of(existingForecast));

        when(clock.instant()).thenReturn(NOW);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(forecastItem(forecastAt, 20.0, 800))
        );

        weatherForecastService.saveForecasts(location, response);

        verify(weatherForecastRepository).save(existingForecast);
        verifyNoInteractions(weatherNotificationMessageBuilder);
    }

    private WeatherForecast previousForecast(
        UUID locationId,
        Instant forecastAt,
        double temperature,
        int weatherCode
    ) {
        return new WeatherForecast(
            locationId,
            forecastAt,
            NOW.minusSeconds(86400),
            temperature,
            (short) 65,
            weatherCode,
            0.2,
            0.0,
            0.0,
            2.5
        );
    }

    private OwmForecastResponse.ForecastItem forecastItem(
        Instant forecastAt,
        double temperature,
        int weatherCode
    ) {
        return new OwmForecastResponse.ForecastItem(
            forecastAt.getEpochSecond(),
            new OwmForecastResponse.Main(temperature, (short) 65),
            List.of(new OwmForecastResponse.WeatherCondition(weatherCode)),
            0.2,
            null,
            null,
            new OwmForecastResponse.Wind(2.5)
        );
    }
}
