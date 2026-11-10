package codeit.ootd.domain.profile.entity;

import codeit.ootd.domain.profile.enums.Gender;
import codeit.ootd.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 프로필. 이름은 users.name 하나만 기준으로 사용하므로 가지지 않는다.
 */
@Getter
@Entity
@Table(name = "profiles", uniqueConstraints = {
    @UniqueConstraint(name = "uk_profiles_user", columnNames = "user_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseTimeEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "grid_x")
    private Integer gridX;

    @Column(name = "grid_y")
    private Integer gridY;

    @Column(name = "location_names", columnDefinition = "TEXT")
    private String locationNames;

    @Column(name = "temperature_sensitivity")
    private Integer temperatureSensitivity;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    public Profile(UUID userId) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
    }

    /**
     * 값이 있는 항목만 바꾼다. null이면 기존 값을 유지한다.
     */
    public void update(Gender gender, LocalDate birthDate, Integer temperatureSensitivity) {
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (temperatureSensitivity != null) {
            this.temperatureSensitivity = temperatureSensitivity;
        }
    }

    /**
     * 위경도만 저장한다. 지명은 저장하지 않고 조회할 때 좌표로 가져온다.
     */
    public void updateLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void changeProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
