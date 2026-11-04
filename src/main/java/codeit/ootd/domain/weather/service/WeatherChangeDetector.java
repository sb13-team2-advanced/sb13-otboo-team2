package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import org.springframework.stereotype.Component;

@Component
public class WeatherChangeDetector {

    private static final double TEMPERATURE_THRESHOLD = 5.0;

    public WeatherChange detect(
        WeatherForecast previous,
        double newTemperature,
        int newWeatherCode
    ) {

        double difference =
            newTemperature - previous.getTemperature();

        TemperatureChange temperatureChange = TemperatureChange.NONE;

        if (difference >= TEMPERATURE_THRESHOLD) {
            temperatureChange = TemperatureChange.UP;
        } else if (difference <= -TEMPERATURE_THRESHOLD) {
            temperatureChange = TemperatureChange.DOWN;
        }

        PrecipitationType previousType =
            precipitationType(previous.getWeatherCode());

        PrecipitationType newType =
            precipitationType(newWeatherCode);

        PrecipitationType precipitationChange =
            previousType != newType
                ? newType
                : PrecipitationType.NONE;

        return new WeatherChange(
            temperatureChange,
            precipitationChange
        );
    }

    private PrecipitationType precipitationType(int weatherCode) {

        if ((weatherCode >= 200 && weatherCode < 400)
            || (weatherCode >= 500 && weatherCode < 600)) {
            return PrecipitationType.RAIN;
        }

        if (weatherCode >= 600 && weatherCode < 700) {
            return PrecipitationType.SNOW;
        }

        return PrecipitationType.NONE;
    }

    public enum TemperatureChange {
        NONE, UP, DOWN;
    }

    public enum PrecipitationType {
        NONE, RAIN, SNOW;
    }

    public record WeatherChange(
        TemperatureChange temperature,
        PrecipitationType precipitation
    ) {
        public boolean hasChanged() {
            return temperature != TemperatureChange.NONE
                || precipitation != PrecipitationType.NONE;
        }
    }
}
