package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherForecastRepository weatherForecastRepository;
    private final WeatherRepository weatherRepository;

    @Transactional
    public void calculateAndSaveDailyWeather(
        WeatherLocation location,
        LocalDate weatherDate
    ) {

        ZoneId koreaZone = ZoneId.of("Asia/Seoul");

        Instant start = weatherDate
            .atStartOfDay(koreaZone)
            .toInstant();

        Instant end = weatherDate.plusDays(1)
            .atStartOfDay(koreaZone)
            .toInstant();

        List<WeatherForecast> forecasts =
            weatherForecastRepository.findForecastsForPeriod(
                location.getId(),
                start,
                end
            );

        if (forecasts.isEmpty()) {
            return;
        }

        double temperatureAvg = forecasts.stream()
            .mapToDouble(WeatherForecast::getTemperature)
            .average()
            .orElseThrow();

        double temperatureMin = forecasts.stream()
            .mapToDouble(WeatherForecast::getTemperature)
            .min()
            .orElseThrow();

        double temperatureMax = forecasts.stream()
            .mapToDouble(WeatherForecast::getTemperature)
            .max()
            .orElseThrow();

        double humidityAvg = forecasts.stream()
            .mapToDouble(WeatherForecast::getHumidity)
            .average()
            .orElseThrow();

        double windSpeed = forecasts.stream()
            .mapToDouble(WeatherForecast::getWindSpeed)
            .max()
            .orElseThrow();

        Optional<WeatherForecast> nextDayStartForecast =
            weatherForecastRepository.findByLocationIdAndForecastAt(
                location.getId(),
                end
            );

        List<WeatherForecast> precipitationForecasts =
            new ArrayList<>(
                forecasts.stream()
                    .filter(f -> f.getForecastAt().isAfter(start))
                    .toList()
            );

        nextDayStartForecast.ifPresent(precipitationForecasts::add);

        double precipitationProbability = precipitationForecasts.stream()
            .mapToDouble(WeatherForecast::getPrecipitationProbability)
            .max()
            .orElse(0.0);

        double rainAmount = precipitationForecasts.stream()
            .mapToDouble(WeatherForecast::getRainAmount)
            .sum();

        double snowAmount = precipitationForecasts.stream()
            .mapToDouble(WeatherForecast::getSnowAmount)
            .sum();

        Map<Integer, Long> codeCounts = forecasts.stream()
            .collect(Collectors.groupingBy(
                WeatherForecast::getWeatherCode,
                Collectors.counting()
            ));

        int weatherCode = forecasts.stream()
            .max(Comparator
                .comparingLong((WeatherForecast f) ->
                    codeCounts.get(f.getWeatherCode()))
                .thenComparingInt(f ->
                    isPrecipitation(f.getWeatherCode()) ? 1 : 0)
                .thenComparing(
                    WeatherForecast::getForecastAt,
                    Comparator.reverseOrder()
                ))
            .orElseThrow()
            .getWeatherCode();

        short humidity = (short) Math.round(humidityAvg);

        short forecastSlotCount = (short) forecasts.size();

        Instant calculatedAt = Instant.now();

        Optional<Weather> existingWeather =
            weatherRepository.findByLocationIdAndWeatherDate(
                location.getId(),
                weatherDate
            );

        Weather dailyWeather;

        if (existingWeather.isPresent()) {

            dailyWeather = existingWeather.get();

            dailyWeather.updateWeather(
                calculatedAt,
                temperatureAvg,
                temperatureMin,
                temperatureMax,
                humidity,
                weatherCode,
                precipitationProbability,
                rainAmount,
                snowAmount,
                windSpeed,
                forecastSlotCount
            );

        } else {

            dailyWeather = new Weather(
                location.getId(),
                weatherDate,
                calculatedAt,
                temperatureAvg,
                temperatureMin,
                temperatureMax,
                humidity,
                weatherCode,
                precipitationProbability,
                rainAmount,
                snowAmount,
                windSpeed,
                forecastSlotCount
            );
        }

        weatherRepository.save(dailyWeather);
    }

    private boolean isPrecipitation(int weatherCode) {
        return (weatherCode >= 200 && weatherCode < 400)
            || (weatherCode >= 500 && weatherCode < 700);
    }

}
