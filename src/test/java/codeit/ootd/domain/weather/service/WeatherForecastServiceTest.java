package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherForecastServiceTest {

    @Mock
    private WeatherForecastRepository weatherForecastRepository;

    @InjectMocks
    private WeatherForecastService weatherForecastService;

    @Test
    @DisplayName("기존 예보가 없으면 새로운 예보를 저장한다")
    void saveForecastsCreatesNewForecast() {

        UUID locationId = UUID.randomUUID();

        WeatherLocation location =
            org.mockito.Mockito.mock(WeatherLocation.class);

        when(location.getId()).thenReturn(locationId);

        Instant forecastAt =
            Instant.parse("2026-10-27T06:00:00Z");

        OwmForecastResponse.ForecastItem item =
            new OwmForecastResponse.ForecastItem(
                forecastAt.getEpochSecond(),
                new OwmForecastResponse.Main(18.5, (short) 65),
                List.of(
                    new OwmForecastResponse.WeatherCondition(800)
                ),
                0.2,
                null,
                null,
                new OwmForecastResponse.Wind(2.5)
            );

        OwmForecastResponse response =
            new OwmForecastResponse(List.of(item));

        when(weatherForecastRepository
            .findByLocationIdAndForecastAt(locationId, forecastAt))
            .thenReturn(Optional.empty());

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
    }

    @Test
    @DisplayName("기존 예보가 있으면 최신 예보로 갱신한다")
    void saveForecastsUpdatesExistingForecast() {

        UUID locationId = UUID.randomUUID();

        WeatherLocation location =
            org.mockito.Mockito.mock(WeatherLocation.class);

        when(location.getId()).thenReturn(locationId);

        Instant forecastAt =
            Instant.parse("2026-10-27T06:00:00Z");

        Instant oldCollectedAt =
            Instant.parse("2026-10-23T00:00:00Z");

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

        OwmForecastResponse.ForecastItem item =
            new OwmForecastResponse.ForecastItem(
                forecastAt.getEpochSecond(),
                new OwmForecastResponse.Main(15.0, (short) 75),
                List.of(new OwmForecastResponse.WeatherCondition(500)),
                0.8,
                new OwmForecastResponse.Precipitation(1.3),
                null,
                new OwmForecastResponse.Wind(3.5)
            );

        OwmForecastResponse response = new OwmForecastResponse(List.of(item));

        when(weatherForecastRepository
            .findByLocationIdAndForecastAt(locationId, forecastAt))
            .thenReturn(Optional.of(existingForecast));

        weatherForecastService.saveForecasts(location, response);

        verify(weatherForecastRepository).save(existingForecast);

        assertThat(existingForecast.getTemperature()).isEqualTo(15.0);
        assertThat(existingForecast.getHumidity()).isEqualTo((short) 75);
        assertThat(existingForecast.getWeatherCode()).isEqualTo(500);
        assertThat(existingForecast.getPrecipitationProbability()).isEqualTo(0.8);
        assertThat(existingForecast.getRainAmount()).isEqualTo(1.3);
        assertThat(existingForecast.getCollectedAt()).isAfter(oldCollectedAt);
    }
}