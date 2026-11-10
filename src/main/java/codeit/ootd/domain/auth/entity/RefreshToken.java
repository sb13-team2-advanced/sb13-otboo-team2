package codeit.ootd.domain.auth.entity;

import codeit.ootd.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private RefreshToken(
            UUID sessionId,
            String tokenHash,
            Instant expiresAt
    ) {

        if (sessionId == null) {
            throw new IllegalArgumentException("세션 ID는 필수입니다.");
        }

        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("토큰 해시는 필수입니다.");
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException("만료 시간은 필수입니다.");
        }

        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(
            UUID sessionId,
            String tokenHash,
            Instant expiresAt
    ) {
        return new RefreshToken(sessionId, tokenHash, expiresAt);
    }

    public void rotate(String newTokenHash, Instant newExpiresAt) {
        if (newTokenHash == null || newTokenHash.isBlank()) {
            throw new IllegalArgumentException("토큰 해시는 필수입니다.");
        }

        if (newExpiresAt == null) {
            throw new IllegalArgumentException("만료 시간은 필수입니다.");
        }

        this.tokenHash = newTokenHash;
        this.expiresAt = newExpiresAt;
    }
}