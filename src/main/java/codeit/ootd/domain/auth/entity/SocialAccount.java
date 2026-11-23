package codeit.ootd.domain.auth.entity;

import codeit.ootd.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "oauth_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_oauth_accounts_provider_user",
                        columnNames = {"provider", "provider_user_id"}
                ),
                @UniqueConstraint(
                        name = "uk_oauth_accounts_user_provider",
                        columnNames = {"user_id", "provider"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    public static SocialAccount create(
            UUID userId,
            OAuthProvider provider,
            String providerUserId
    ) {
        SocialAccount account = new SocialAccount();

        account.userId = Objects.requireNonNull(userId);
        account.provider = Objects.requireNonNull(provider);
        account.providerUserId =
                Objects.requireNonNull(providerUserId);

        if (providerUserId.isBlank()) {
            throw new IllegalArgumentException(
                    "소셜 사용자 ID는 비어 있을 수 없습니다."
            );
        }

        return account;
    }
}
