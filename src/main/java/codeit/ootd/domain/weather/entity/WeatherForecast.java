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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id", nullable = false)
  private WeatherLocation location;

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
}
