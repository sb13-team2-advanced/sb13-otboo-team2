package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.infra.weather.OpenWeatherMapClient;
import codeit.ootd.infra.weather.OwmHourlyForecastConverter;
import codeit.ootd.infra.weather.OwmHourlyForecastConverter.ThreeHourForecast;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse.HourlyItem;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherForecastCollector {

    private final OpenWeatherMapClient openWeatherMapClient;
    private final WeatherForecastService weatherForecastService;
    private final OwmHourlyForecastConverter hourlyForecastConverter;
    private final Clock clock;

    public void collectForecasts(WeatherLocation location) {

        OwmForecastResponse response = openWeatherMapClient.getForecast(
            location.getLatitude(),
            location.getLongitude()
        );

        weatherForecastService.saveForecasts(location, response);
    }

    public void collectHistoricalForecasts(WeatherLocation location) {

        List<HourlyItem> hourlyItems =
            openWeatherMapClient.getTodayHourlyHistory(
                location.getLatitude(),
                location.getLongitude(),
                clock.instant()
            );

        List<ThreeHourForecast> forecasts =
            hourlyForecastConverter.convert(hourlyItems);

        weatherForecastService.saveHistoricalForecasts(location, forecasts);       
    }
}
