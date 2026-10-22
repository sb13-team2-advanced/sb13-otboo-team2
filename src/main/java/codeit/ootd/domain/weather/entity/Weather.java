package codeit.ootd.domain.weather.entity;

import codeit.ootd.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "weather",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_weather_location_date",
        columnNames = {"location_id", "weather_date"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weather extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private WeatherLocation location;

    @Column(nullable = false)
    private LocalDate weatherDate;

    @Column(nullable = false)
    private Instant calculatedAt;

    @Column(nullable = false)
    private double temperatureAvg;

    @Column(nullable = false)
    private double temperatureMin;

    @Column(nullable = false)
    private double temperatureMax;

    @Column(nullable = false)
    private short humidityAvg;

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

    @Column(nullable = false)
    private short forecastSlotCount;

    public Weather(
        WeatherLocation location,
        LocalDate weatherDate,
        Instant calculatedAt,
        double temperatureAvg,
        double temperatureMin,
        double temperatureMax,
        short humidityAvg,
        int weatherCode,
        double precipitationProbability,
        double rainAmount,
        double snowAmount,
        double windSpeed,
        short forecastSlotCount
    ) {
        this.location = location;
        this.weatherDate = weatherDate;
        this.calculatedAt = calculatedAt;
        this.temperatureAvg = temperatureAvg;
        this.temperatureMin = temperatureMin;
        this.temperatureMax = temperatureMax;
        this.humidityAvg = humidityAvg;
        this.weatherCode = weatherCode;
        this.precipitationProbability = precipitationProbability;
        this.rainAmount = rainAmount;
        this.snowAmount = snowAmount;
        this.windSpeed = windSpeed;
        this.forecastSlotCount = forecastSlotCount;
    }

    public void updateWeather(
        Instant calculatedAt,
        double temperatureAvg,
        double temperatureMin,
        double temperatureMax,
        short humidityAvg,
        int weatherCode,
        double precipitationProbability,
        double rainAmount,
        double snowAmount,
        double windSpeed,
        short forecastSlotCount
    ) {
        if (this.calculatedAt != null && calculatedAt.isBefore(this.calculatedAt)) {
            throw new IllegalArgumentException("기존 날짜보다 오래된 집계 결과입니다.");
        }

        if (forecastSlotCount <= 0) {
            throw new IllegalArgumentException("집계에 사용된 예보가 없습니다.");
        }

        this.calculatedAt = calculatedAt;
        this.temperatureAvg = temperatureAvg;
        this.temperatureMin = temperatureMin;
        this.temperatureMax = temperatureMax;
        this.humidityAvg = humidityAvg;
        this.weatherCode = weatherCode;
        this.precipitationProbability = precipitationProbability;
        this.rainAmount = rainAmount;
        this.snowAmount = snowAmount;
        this.windSpeed = windSpeed;
        this.forecastSlotCount = forecastSlotCount;       
    }
}
