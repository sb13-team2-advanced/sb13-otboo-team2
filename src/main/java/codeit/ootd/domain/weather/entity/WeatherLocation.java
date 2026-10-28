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

    public WeatherLocation(
        String cellId,
        double latitude,
        double longitude,
        Instant nextRefreshAt
    ) {
        this.cellId = cellId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.nextRefreshAt = nextRefreshAt;
    }

    public void completeCollection(
        Instant collectedAt,
        Instant nextRefreshAt
    ) {
        if (!nextRefreshAt.isAfter(collectedAt)) {
            throw new IllegalArgumentException("다음 수집 예정 시간은 수집 시간보다 이후여야 합니다.");
        }

        this.lastCollectedAt = collectedAt;
        this.nextRefreshAt = nextRefreshAt;
    }
}
