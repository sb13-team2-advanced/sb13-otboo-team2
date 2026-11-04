package codeit.ootd.domain.weather.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.infra.weather.OpenWeatherMapClient;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("날씨 예보 수집 연결 테스트")
class WeatherForecastCollectorTest {

    @Mock
    private OpenWeatherMapClient openWeatherMapClient;

    @Mock
    private WeatherForecastService weatherForecastService;

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
}
