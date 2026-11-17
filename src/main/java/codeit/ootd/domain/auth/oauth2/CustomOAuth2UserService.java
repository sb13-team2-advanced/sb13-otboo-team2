package codeit.ootd.domain.auth.oauth2;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService
        implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate;
    private final OAuth2UserInfoExtractor extractor;
    private final OAuth2AccountResolver resolver;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request)
            throws OAuth2AuthenticationException {

        String registrationId =
                request.getClientRegistration().getRegistrationId();

        OAuthProvider provider;

        try {
            provider = OAuthProvider.valueOf(
                    registrationId.toUpperCase(java.util.Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new OAuth2AuthenticationException(
                    "unsupported_oauth_provider"
            );
        }

        OAuth2User providerUser = delegate.loadUser(request);

        OAuth2UserInfo userInfo = extractor.extract(
                provider,
                providerUser.getAttributes()
        );

        UUID userId = resolver.resolveUserId(userInfo);

        Map<String, Object> attributes =
                new HashMap<>(providerUser.getAttributes());

        attributes.put("otbooUserId", userId.toString());

        return new DefaultOAuth2User(
                providerUser.getAuthorities(),
                attributes,
                "otbooUserId"
        );
    }
}
