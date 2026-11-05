package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class WeatherChangeDetector {

    private static final BigDecimal TEMPERATURE_THRESHOLD = BigDecimal.valueOf(5);

    public WeatherChange detect(
        WeatherForecast previous,
        double newTemperature,
        int newWeatherCode
    ) {

        BigDecimal difference = BigDecimal.valueOf(newTemperature)
            .subtract(BigDecimal.valueOf(previous.getTemperature()));

        TemperatureChange temperatureChange = TemperatureChange.NONE;

        if (difference.compareTo(TEMPERATURE_THRESHOLD) >= 0) {
            temperatureChange = TemperatureChange.UP;
        } else if (difference.compareTo(TEMPERATURE_THRESHOLD.negate()) <= 0) {
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

        if ((weatherCode >= 611 && weatherCode <= 613)
            || weatherCode == 615 || weatherCode == 616
        ) {
            return PrecipitationType.MIXED;
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
        NONE, RAIN, SNOW, MIXED;
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
