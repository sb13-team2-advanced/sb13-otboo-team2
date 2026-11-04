package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.service.WeatherChangeDetector.PrecipitationType;
import codeit.ootd.domain.weather.service.WeatherChangeDetector.TemperatureChange;
import codeit.ootd.domain.weather.service.WeatherChangeDetector.WeatherChange;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeatherChangeDetectorTest {

    private final WeatherChangeDetector detector = new WeatherChangeDetector();

    @ParameterizedTest
    @DisplayName("기온 변화가 5도 이상일 때만 감지한다")
    @CsvSource({
        "25.0, UP",
        "15.0, DOWN",
        "24.9, NONE",
        "15.1, NONE"
    })
    void detectsTemperatureChange(
        double newTemperature,
        TemperatureChange expected
    ) {
        WeatherForecast previous = previousForecast(20.0, 800);

        WeatherChange change = detector.detect(
            previous,
            newTemperature,
            800
        );

        assertThat(change.temperature()).isEqualTo(expected);
        assertThat(change.precipitation()).isEqualTo(PrecipitationType.NONE);
        assertThat(change.hasChanged())
            .isEqualTo(expected != TemperatureChange.NONE);
    }

    @ParameterizedTest
    @DisplayName("비와 눈이 새롭게 예보될 때만 감지한다")
    @CsvSource({
        "800, 500, RAIN",
        "804, 600, SNOW",
        "600, 500, RAIN",
        "500, 600, SNOW",
        "800, 200, RAIN",
        "800, 300, RAIN",
        "500, 501, NONE",
        "600, 601, NONE",
        "500, 800, NONE",
        "600, 804, NONE"
    })
    void detectsPrecipitationChange(
        int previousCode,
        int newCode,
        PrecipitationType expected
    ) {
        WeatherForecast previous = previousForecast(20.0, previousCode);

        WeatherChange change = detector.detect(
            previous,
            20.0,
            newCode
        );

        assertThat(change.temperature()).isEqualTo(TemperatureChange.NONE);
        assertThat(change.precipitation()).isEqualTo(expected);
        assertThat(change.hasChanged())
            .isEqualTo(expected != PrecipitationType.NONE);
    }

    @ParameterizedTest
    @DisplayName("기온과 강수 변화를 함께 감지한다")
    @CsvSource({
        "25.0, 500, UP, RAIN",
        "15.0, 500, DOWN, RAIN",
        "25.0, 600, UP, SNOW",
        "15.0, 600, DOWN, SNOW"
    })
    void detectsCombinedWeatherChange(
        double newTemperature,
        int newWeatherCode,
        TemperatureChange expectedTemperature,
        PrecipitationType expectedPrecipitation
    ) {
        WeatherForecast previous = previousForecast(20.0, 800);

        WeatherChange change = detector.detect(
            previous,
            newTemperature,
            newWeatherCode
        );

        assertThat(change.temperature()).isEqualTo(expectedTemperature);
        assertThat(change.precipitation()).isEqualTo(expectedPrecipitation);
        assertThat(change.hasChanged()).isTrue();
    }

    private WeatherForecast previousForecast(
        double temperature,
        int weatherCode
    ) {
        return new WeatherForecast(
            UUID.randomUUID(),
            Instant.parse("2026-11-04T03:00:00Z"),
            Instant.parse("2026-11-04T00:00:00Z"),
            temperature,
            (short) 60,
            weatherCode,
            0.0,
            0.0,
            0.0,
            2.0
        );
    }
}
