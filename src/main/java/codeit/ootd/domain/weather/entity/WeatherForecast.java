package codeit.ootd.domain.weather.entity;

import codeit.ootd.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "weather_forecast",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_weather_forecast_location_time",
        columnNames = {"location_id", "forecast_at"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherForecast extends BaseTimeEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(nullable = false)
    private Instant forecastAt;

    @Column(nullable = false)
    private Instant collectedAt;

    @Column(nullable = false)
    private double temperature;

    @Column(nullable = false)
    private short humidity;

    @Column(nullable = false)
    private int weatherCode;

    @Column(nullable = false)
    private double precipitationProbability;

    @Column(nullable = false)
    private double rainAmount;

    @Column(nullable = false)
    private double snowAmount;

    @Column(nullable = false)
    private double windSpeed;

    public WeatherForecast(
        UUID locationId,
        Instant forecastAt,
        Instant collectedAt,
        double temperature,
        short humidity,
        int weatherCode,
        double precipitationProbability,
        double rainAmount,
        double snowAmount,
        double windSpeed
    ) {
        this.locationId = locationId;
        this.forecastAt = forecastAt;
        this.collectedAt = collectedAt;
        this.temperature = temperature;
        this.humidity = humidity;
        this.weatherCode = weatherCode;
        this.precipitationProbability = precipitationProbability;
        this.rainAmount = rainAmount;
        this.snowAmount = snowAmount;
        this.windSpeed = windSpeed;
    }

    public void updateForecast(
        Instant collectedAt,
        double temperature,
        short humidity,
        int weatherCode,
        double precipitationProbability,
        double rainAmount,
        double snowAmount,
        double windSpeed
    ) {
        if (collectedAt == null) {
            throw new IllegalArgumentException("수집 시각은 필수입니다.");
        }

        if (collectedAt.isBefore(this.collectedAt)) {
            throw new IllegalArgumentException("기존 예보보다 오래된 수집 데이터입니다.");
        }

        this.collectedAt = collectedAt;
        this.temperature = temperature;
        this.humidity = humidity;
        this.weatherCode = weatherCode;
        this.precipitationProbability = precipitationProbability;
        this.rainAmount = rainAmount;
        this.snowAmount = snowAmount;
        this.windSpeed = windSpeed;
    }
}
