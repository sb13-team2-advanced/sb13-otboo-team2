package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.mapper.WeatherDtoMapper;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import java.time.Clock;
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
    private final WeatherLocationService weatherLocationService;
    private final WeatherDtoMapper weatherDtoMapper;
    private final Clock clock;

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
            .average()
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

    public List<WeatherDto> getWeathers(double latitude, double longitude) {

        WeatherLocation location =
            weatherLocationService.findOrCreate(latitude, longitude);

        LocalDate today = LocalDate.now(
            clock.withZone(ZoneId.of("Asia/Seoul"))
        );

        List<Weather> weathers =
            weatherRepository.findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                location.getId(),
                today.minusDays(1),
                today.plusDays(4)
            );

        if (weathers.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, Weather> weatherByDate = weathers.stream()
            .collect(Collectors.toMap(
                Weather::getWeatherDate,
                weather -> weather
            ));

        WeatherAPILocation locationDto =
            weatherLocationService.getLocation(latitude, longitude);

        return weathers.stream()
            .filter(weather -> !weather.getWeatherDate().isBefore(today))
            .map(weather -> weatherDtoMapper.toDto(
                weather,
                weatherByDate.get(weather.getWeatherDate().minusDays(1)),
                locationDto
            ))
            .toList();
    }

    private boolean isPrecipitation(int weatherCode) {
        return (weatherCode >= 200 && weatherCode < 400)
            || (weatherCode >= 500 && weatherCode < 700);
    }
}
