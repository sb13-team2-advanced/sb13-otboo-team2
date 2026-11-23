package codeit.ootd.domain.auth.oauth2;

import java.util.UUID;

public interface OAuth2AccountResolver {

    UUID resolveUserId(OAuth2UserInfo userInfo);
}