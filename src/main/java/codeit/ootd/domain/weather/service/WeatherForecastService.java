package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.infra.weather.OwmHourlyForecastConverter.ThreeHourForecast;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeatherForecastService {

    private final WeatherForecastRepository weatherForecastRepository;
    private final WeatherChangeDetector weatherChangeDetector;
    private final WeatherNotificationMessageBuilder weatherNotificationMessageBuilder;
    private final Clock clock;

    private static final String NOTIFICATION_TITLE = "날씨 예보가 달라졌어요!";

    private final WeatherNotificationRecipientFinder recipientFinder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void saveForecasts(
        WeatherLocation location,
        OwmForecastResponse response
    ) {

        Instant collectedAt = clock.instant();

        Instant windowEnd = collectedAt.plus(Duration.ofHours(6));

        List<Instant> forecastAts = response.list().stream()
            .map(item -> Instant.ofEpochSecond(item.dt()))
            .distinct()
            .toList();

        Map<Instant, WeatherForecast> existingForecasts = new HashMap<>();

        if (!forecastAts.isEmpty()) {
            weatherForecastRepository.findByLocationIdAndForecastAtIn(
                location.getId(),
                forecastAts
            ).forEach(forecast ->
                existingForecasts.put(forecast.getForecastAt(), forecast)
            );
        }

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

            WeatherForecast forecast = existingForecasts.get(forecastAt);

            if (forecast != null) {

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

            } else {

                forecast = new WeatherForecast(
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
            }

            weatherForecastRepository.save(forecast);
            existingForecasts.put(forecastAt, forecast);
        }

        if (changes.isEmpty()) {
            return;
        }

        boolean temperatureUp = false;
        boolean temperatureDown = false;

        Set<WeatherChangeDetector.PrecipitationType> precipitationTypes = new LinkedHashSet<>();

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

        Set<UUID> recipientIds =
            recipientFinder.findRecipientIds(location.getCellId());

        for (UUID recipientId : recipientIds) {
            eventPublisher.publishEvent(
                new NotificationRequestedEvent(
                    recipientId,
                    NOTIFICATION_TITLE,
                    content,
                    NotificationLevel.INFO
                )
            );
        }
    }

    @Transactional
    public void saveHistoricalForecasts(
        WeatherLocation location,
        List<ThreeHourForecast> forecasts
    ) {
        Instant collectedAt = clock.instant();

        List<Instant> forecastAts = forecasts.stream()
            .map(ThreeHourForecast::forecastAt)
            .filter(forecastAt -> forecastAt.isBefore(collectedAt))
            .distinct()
            .toList();

        if (forecastAts.isEmpty()) {
            return;
        }

        Map<Instant, WeatherForecast> existingForecasts = new HashMap<>();

        weatherForecastRepository.findByLocationIdAndForecastAtIn(
            location.getId(),
            forecastAts
        ).forEach(forecast ->
            existingForecasts.put(forecast.getForecastAt(), forecast)
        );

        for (ThreeHourForecast item : forecasts) {

            Instant forecastAt = item.forecastAt();

            if (!forecastAt.isBefore(collectedAt)) {
                continue;
            }

            WeatherForecast forecast = existingForecasts.get(forecastAt);

            if (forecast != null) {

                forecast.updateForecast(
                    collectedAt,
                    item.temperature(),
                    item.humidity(),
                    item.weatherCode(),
                    item.precipitationProbability(),
                    item.rainAmount(),
                    item.snowAmount(),
                    item.windSpeed()
                );

            } else {

                forecast = new WeatherForecast(
                    location.getId(),
                    forecastAt,
                    collectedAt,
                    item.temperature(),
                    item.humidity(),
                    item.weatherCode(),
                    item.precipitationProbability(),
                    item.rainAmount(),
                    item.snowAmount(),
                    item.windSpeed()
                );
            }

            weatherForecastRepository.save(forecast);
            existingForecasts.put(forecastAt, forecast);
        }
    }   
}
