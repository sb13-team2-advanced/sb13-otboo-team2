package codeit.ootd.domain.auth.oauth2.linking;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import java.time.Instant;
import java.util.UUID;

public record OAuth2LinkRequest(
        UUID userId,
        OAuthProvider provider,
        Instant expiresAt
) {
}

