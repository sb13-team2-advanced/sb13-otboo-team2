package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.entity.OAuthProvider;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SocialAccountService {

    Optional<UUID> findUserId(
            OAuthProvider provider,
            String providerUserId
    );

    void linkAccount(
            UUID userId,
            OAuthProvider provider,
            String providerUserId
    );

    Map<String, Boolean> getLinkedProviders(UUID userId);
}
