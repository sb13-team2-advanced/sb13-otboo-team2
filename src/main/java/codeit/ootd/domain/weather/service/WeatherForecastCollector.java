package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.infra.weather.OpenWeatherMapClient;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherForecastCollector {

    private final OpenWeatherMapClient openWeatherMapClient;
    private final WeatherForecastService weatherForecastService;

    public void collectForecasts(WeatherLocation location) {

        OwmForecastResponse response = openWeatherMapClient.getForecast(
            location.getLatitude(),
            location.getLongitude()
        );

        weatherForecastService.saveForecasts(location, response);
    }
}
