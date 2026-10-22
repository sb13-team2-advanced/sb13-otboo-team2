package codeit.ootd.domain.weather.entity;

import codeit.ootd.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "weather_location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherLocation extends BaseTimeEntity {

  @Column(nullable = false, unique = true, length = 32)
  private String cellId;

  @Column(nullable = false)
  private short resolution = 8;

  @Column(nullable = false)
  private double latitude;

  @Column(nullable = false)
  private double longitude;

  private Instant lastCollectedAt;

  @Column(nullable = false)
  private Instant nextRefreshAt;
}
