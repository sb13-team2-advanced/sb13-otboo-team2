package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeatherForecastService {

    private final WeatherForecastRepository weatherForecastRepository;
    private final WeatherChangeDetector weatherChangeDetector;
    private final WeatherNotificationMessageBuilder weatherNotificationMessageBuilder;
    private final Clock clock;

    @Transactional
    public void saveForecasts(
        WeatherLocation location,
        OwmForecastResponse response
    ) {

        Instant collectedAt = clock.instant();

        Instant windowEnd = collectedAt.plus(Duration.ofHours(6));

        Map<Instant, WeatherChangeDetector.WeatherChange> changes = new TreeMap<>();

        for (OwmForecastResponse.ForecastItem item : response.list()) {

            Instant forecastAt = Instant.ofEpochSecond(item.dt());

            double temperature = item.main().temp();
            short humidity = item.main().humidity();
            int weatherCode = item.weather().get(0).id();

            double precipitationProbability = item.pop();

            double rainAmount = item.rain() == null
                ? 0.0
                : item.rain().amount();

            double snowAmount = item.snow() == null
                ? 0.0
                : item.snow().amount();

            double windSpeed = item.wind().speed();

            Optional<WeatherForecast> existingForecast =
                weatherForecastRepository.findByLocationIdAndForecastAt(
                    location.getId(),
                    forecastAt
                );

            if (existingForecast.isPresent()) {

                WeatherForecast forecast = existingForecast.get();

                if (forecastAt.isAfter(collectedAt)
                    && !forecastAt.isAfter(windowEnd)) {

                    WeatherChangeDetector.WeatherChange change =
                        weatherChangeDetector.detect(forecast, temperature, weatherCode);

                    if (change.hasChanged()) {
                        changes.put(forecastAt, change);
                    }
                }

                forecast.updateForecast(
                    collectedAt,
                    temperature,
                    humidity,
                    weatherCode,
                    precipitationProbability,
                    rainAmount,
                    snowAmount,
                    windSpeed
                );

                weatherForecastRepository.save(forecast);

            } else {

                WeatherForecast forecast = new WeatherForecast(
                    location.getId(),
                    forecastAt,
                    collectedAt,
                    temperature,
                    humidity,
                    weatherCode,
                    precipitationProbability,
                    rainAmount,
                    snowAmount,
                    windSpeed
                );

                weatherForecastRepository.save(forecast);
            }
        }

        if (changes.isEmpty()) {
            return;
        }

        boolean temperatureUp = false;
        boolean temperatureDown = false;

        Set<WeatherChangeDetector.PrecipitationType> precipitationTypes =
            new LinkedHashSet<>();

        for (WeatherChangeDetector.WeatherChange change : changes.values()) {

            if (change.temperature() == WeatherChangeDetector.TemperatureChange.UP) {
                temperatureUp = true;
            } else if (change.temperature() == WeatherChangeDetector.TemperatureChange.DOWN) {
                temperatureDown = true;
            }

            if (change.precipitation() != WeatherChangeDetector.PrecipitationType.NONE) {
                precipitationTypes.add(change.precipitation());
            }
        }

        String content = weatherNotificationMessageBuilder.build(
            temperatureUp,
            temperatureDown,
            precipitationTypes
        );
    }
}
