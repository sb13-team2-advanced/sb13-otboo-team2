package codeit.ootd.domain.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherForecastTest {

    @Test
    @DisplayName("새로운 예보를 수집하면 기존 예보 정보가 갱신된다")
    void updateForecastWithNewCollection() {

        Instant collectedAt12 =
            OffsetDateTime.parse("2026-10-23T12:00:00+09:00")
                .toInstant();

        Instant collectedAt15 =
            OffsetDateTime.parse("2026-10-23T15:00:00+09:00")
                .toInstant();

        Instant forecastAt18 =
            OffsetDateTime.parse("2026-10-23T18:00:00+09:00")
                .toInstant();

        WeatherLocation location = new WeatherLocation(
            "8828308281fffff",
            37.5665,
            126.9780,
            collectedAt15
        );

        WeatherForecast forecast = new WeatherForecast(
            location,
            forecastAt18,
            collectedAt12,
            18.0,
            (short) 60,
            800,
            0.0,
            0.0,
            0.0,
            2.0
        );

        forecast.updateForecast(
            collectedAt15,
            15.0,
            (short) 75,
            500,
            0.8,
            1.3,
            0.0,
            3.5
        );

        assertThat(forecast.getForecastAt())
            .isEqualTo(forecastAt18);

        assertThat(forecast.getCollectedAt())
            .isEqualTo(collectedAt15);

        assertThat(forecast.getTemperature())
            .isEqualTo(15.0);

        assertThat(forecast.getHumidity())
            .isEqualTo((short) 75);

        assertThat(forecast.getWeatherCode())
            .isEqualTo(500);
    }

    @Test
    @DisplayName("오래된 수집 데이터로 최신 예보를 덮어쓸 수 없다")
    void updateForecastRejectsOlderCollection() {

        Instant collectedAt12 =
            OffsetDateTime.parse("2026-10-23T12:00:00+09:00")
                .toInstant();

        Instant collectedAt15 =
            OffsetDateTime.parse("2026-10-23T15:00:00+09:00")
                .toInstant();

        Instant forecastAt18 =
            OffsetDateTime.parse("2026-10-23T18:00:00+09:00")
                .toInstant();

        WeatherLocation location = new WeatherLocation(
            "8828308281fffff",
            37.5665,
            126.9780,
            collectedAt15
        );

        WeatherForecast forecast = new WeatherForecast(
            location,
            forecastAt18,
            collectedAt15,
            15.0,
            (short) 75,
            500,
            0.8,
            1.3,
            0.0,
            3.5
        );

        assertThatThrownBy(() ->
            forecast.updateForecast(
                collectedAt12,
                18.0,
                (short) 60,
                800,
                0.0,
                0.0,
                0.0,
                2.0
            )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(forecast.getCollectedAt())
            .isEqualTo(collectedAt15);

        assertThat(forecast.getTemperature())
            .isEqualTo(15.0);

        assertThat(forecast.getWeatherCode())
            .isEqualTo(500);
    }
}