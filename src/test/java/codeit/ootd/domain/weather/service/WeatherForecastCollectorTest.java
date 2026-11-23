package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.infra.weather.OpenWeatherMapClient;
import codeit.ootd.infra.weather.OwmHourlyForecastConverter;
import codeit.ootd.infra.weather.OwmHourlyForecastConverter.ThreeHourForecast;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse.HourlyItem;
import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse.WeatherCondition;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherForecastCollectorTest {

    @Mock
    private OpenWeatherMapClient openWeatherMapClient;

    @Mock
    private WeatherForecastService weatherForecastService;

    @Mock
    private OwmHourlyForecastConverter hourlyForecastConverter;

    @Mock
    private Clock clock;

    @Mock
    private WeatherLocation location;

    @InjectMocks
    private WeatherForecastCollector weatherForecastCollector;

    @Test
    @DisplayName("외부 API에서 조회한 예보를 저장 서비스에 전달한다")
    void collectForecastsFetchesAndSaves() {

        double latitude = 37.5665;
        double longitude = 126.9780;

        OwmForecastResponse response =
            new OwmForecastResponse(List.of());

        when(location.getLatitude()).thenReturn(latitude);
        when(location.getLongitude()).thenReturn(longitude);

        when(openWeatherMapClient.getForecast(latitude, longitude))
            .thenReturn(response);

        weatherForecastCollector.collectForecasts(location);

        verify(openWeatherMapClient)
            .getForecast(latitude, longitude);

        verify(weatherForecastService)
            .saveForecasts(location, response);
    }

    @Test
    @DisplayName("One Call 시간별 데이터를 3시간 예보로 변환하여 저장한다")
    void collectHistoricalForecastsFetchesConvertsAndSaves() {

        double latitude = 37.5665;
        double longitude = 126.9780;

        Instant collectedAt = Instant.parse("2026-11-13T09:00:00Z");
        Instant forecastAt = Instant.parse("2026-11-13T03:00:00Z");

        HourlyItem hourlyItem = new HourlyItem(
            forecastAt.getEpochSecond(),
            12.5,
            (short) 70,
            2.0,
            List.of(new WeatherCondition(803)),
            null,
            null,
            null
        );

        List<HourlyItem> hourlyItems = List.of(hourlyItem);

        List<ThreeHourForecast> forecasts = List.of(
            new ThreeHourForecast(
                forecastAt,
                12.5,
                (short) 70,
                803,
                null,
                0.0,
                0.0,
                2.0
            )
        );

        when(location.getLatitude()).thenReturn(latitude);
        when(location.getLongitude()).thenReturn(longitude);
        when(clock.instant()).thenReturn(collectedAt);

        when(openWeatherMapClient.getTodayHourlyHistory(
            latitude,
            longitude,
            collectedAt
        )).thenReturn(hourlyItems);

        when(hourlyForecastConverter.convert(hourlyItems))
            .thenReturn(forecasts);

        weatherForecastCollector.collectHistoricalForecasts(location);

        verify(openWeatherMapClient).getTodayHourlyHistory(
            latitude,
            longitude,
            collectedAt
        );

        verify(hourlyForecastConverter).convert(hourlyItems);

        verify(weatherForecastService)
            .saveHistoricalForecasts(location, forecasts);
    }

    @Test
    @DisplayName("One Call 조회에 실패하면 과거 예보를 저장하지 않는다")
    void collectHistoricalForecastsDoesNotSaveWhenApiFails() {

        double latitude = 37.5665;
        double longitude = 126.9780;
        Instant collectedAt = Instant.parse("2026-11-13T09:00:00Z");

        when(location.getLatitude()).thenReturn(latitude);
        when(location.getLongitude()).thenReturn(longitude);
        when(clock.instant()).thenReturn(collectedAt);

        when(openWeatherMapClient.getTodayHourlyHistory(
            latitude,
            longitude,
            collectedAt
        )).thenThrow(new IllegalStateException("One Call 호출 제한 초과"));

        assertThatThrownBy(() ->
            weatherForecastCollector.collectHistoricalForecasts(location))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("One Call 호출 제한 초과");

        verifyNoInteractions(
            hourlyForecastConverter,
            weatherForecastService
        );
    }
}
