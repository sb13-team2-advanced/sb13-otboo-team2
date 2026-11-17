package codeit.ootd.domain.auth.repository;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.entity.SocialAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository
        extends JpaRepository<SocialAccount, UUID> {

    Optional<SocialAccount> findByProviderAndProviderUserId(
            OAuthProvider provider,
            String providerUserId
    );

    boolean existsByUserIdAndProvider(
            UUID userId,
            OAuthProvider provider
    );
}
