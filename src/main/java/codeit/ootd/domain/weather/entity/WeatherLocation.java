package codeit.ootd.domain.weather.entity;

import codeit.ootd.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
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

    @Column(name = "initial_collection_in_progress", nullable = false)
    private boolean initialCollectionInProgress = false;

    @Column(name = "initial_collection_token")
    private UUID initialCollectionToken;

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
        this.initialCollectionInProgress = false;
    }

    public void scheduleRetry(Instant failedAt, Instant retryAt) {
        if (!retryAt.isAfter(failedAt)) {
            throw new IllegalArgumentException("재시도 예정 시간은 실패 시간보다 이후여야 합니다.");
        }

        this.nextRefreshAt = retryAt;
        this.initialCollectionInProgress = false;
    }
}
