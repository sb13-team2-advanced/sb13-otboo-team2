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

    public OwmForecastResponse fetchForecast(WeatherLocation location) {
        return openWeatherMapClient.getForecast(
            location.getLatitude(),
            location.getLongitude()
        );
    }

    public List<ThreeHourForecast> fetchHistoricalForecasts(WeatherLocation location) {

        List<HourlyItem> items = openWeatherMapClient.getTodayHourlyHistory(
            location.getLatitude(),
            location.getLongitude(),
            clock.instant()
        );

        return hourlyForecastConverter.convert(items);
    }

    public void collectForecasts(WeatherLocation location) {

        weatherForecastService.saveForecasts(
            location,
            fetchForecast(location)
        );
    }

    public void collectHistoricalForecasts(WeatherLocation location) {

        weatherForecastService.saveHistoricalForecasts(
            location,
            fetchHistoricalForecasts(location)
        );
    }
}
