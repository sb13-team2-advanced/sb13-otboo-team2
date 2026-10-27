package codeit.ootd.domain.feed.entity;

import codeit.ootd.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "feeds")
@Getter
@Builder
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Feed extends BaseTimeEntity {

    @NonNull
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    @Column(name = "like_count", nullable = false)
    private long likeCount = 0L;

    @Builder.Default
    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @NonNull
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @NonNull
    @Column(name = "weather_id", nullable = false)
    private UUID weatherId;

    @Column(name = "weather_temperature_current", nullable = false)
    private double weatherTemperatureCurrent;

    @Column(
            name = "weather_temperature_compared_to_day_before",
            nullable = false
    )
    private double weatherTemperatureComparedToDayBefore;

    @Column(name = "weather_temperature_min", nullable = false)
    private double weatherTemperatureMin;

    @Column(name = "weather_temperature_max", nullable = false)
    private double weatherTemperatureMax;

    @NonNull
    @Column(name = "weather_sky_status", nullable = false, length = 20)
    private String weatherSkyStatus;

    @NonNull
    @Column(name = "weather_precipitation_type", nullable = false, length = 20)
    private String weatherPrecipitationType;

    @Column(name = "weather_precipitation_amount", nullable = false)
    private double weatherPrecipitationAmount;

    @Column(name = "weather_precipitation_probability", nullable = false)
    private double weatherPrecipitationProbability;

    public void updateContent(@NonNull String content) {
        this.content = content;
    }
}