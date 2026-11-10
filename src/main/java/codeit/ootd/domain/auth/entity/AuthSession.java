package codeit.ootd.domain.auth.entity;

import codeit.ootd.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession extends BaseTimeEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    private AuthSession(UUID userId, Instant expiresAt) {
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public static AuthSession create(UUID userId, Instant expiresAt) {
        return new AuthSession(userId, expiresAt);
    }


    public void revoke(Instant revokedAt) {
        if (this.revokedAt != null) {
            return;
        }

        this.revokedAt = Objects.requireNonNull(
                revokedAt,
                "세션 무효화 시간은 필수입니다."
        );
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}