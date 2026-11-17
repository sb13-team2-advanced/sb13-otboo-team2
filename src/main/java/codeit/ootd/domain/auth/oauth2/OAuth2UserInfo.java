package codeit.ootd.domain.auth.oauth2;

import codeit.ootd.domain.auth.entity.OAuthProvider;

public record OAuth2UserInfo(
        OAuthProvider provider,
        String providerUserId,
        String email,
        String name
) {
}