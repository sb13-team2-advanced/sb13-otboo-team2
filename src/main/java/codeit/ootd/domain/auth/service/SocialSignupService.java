package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.oauth2.OAuth2UserInfo;
import java.util.UUID;

public interface SocialSignupService {

    UUID register(OAuth2UserInfo userInfo);
}
