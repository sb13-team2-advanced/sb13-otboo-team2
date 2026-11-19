package codeit.ootd.domain.auth.oauth2;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

@Component
public class OAuth2UserInfoExtractor {

    public OAuth2UserInfo extract(
            OAuthProvider provider,
            Map<String, Object> attributes
    ) {
        Objects.requireNonNull(provider, "provider는 필수입니다.");
        Objects.requireNonNull(attributes, "attributes는 필수입니다.");

        return switch (provider) {
            case GOOGLE -> extractGoogle(attributes);
            case KAKAO -> extractKakao(attributes);
        };
    }

    private OAuth2UserInfo extractGoogle(
            Map<String, Object> attributes
    ) {
        String providerUserId =
                requiredString(attributes.get("sub"));

        String email = optionalString(attributes.get("email"));

        if (email == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_required"),
                    "Google 계정에서 이메일 정보를 제공하지 않았습니다."
            );
        }

        return new OAuth2UserInfo(
                OAuthProvider.GOOGLE,
                providerUserId,
                email,
                optionalString(attributes.get("name"))
        );
    }


    private OAuth2UserInfo extractKakao(
            Map<String, Object> attributes
    ) {
        Map<String, Object> account =
                nestedMap(attributes.get("kakao_account"));

        Map<String, Object> profile =
                nestedMap(account.get("profile"));

        String providerUserId = requiredString(attributes.get("id"));
        String nickname = optionalString(profile.get("nickname"));
        String email = optionalString(account.get("email"));

        if (email == null) {
            String emailName = nickname != null
                    ? nickname.replaceAll("[^가-힣a-zA-Z0-9]", "")
                    : "kakao";

            if (emailName.isBlank()) {
                emailName = "kakao";
            }

            String suffix = "_" + providerUserId + "@kakao.com";
            int maxNameLength = 100 - suffix.length();

            if (maxNameLength <= 0) {
                throw new IllegalArgumentException(
                        "Kakao 사용자 ID가 너무 깁니다."
                );
            }

            if (emailName.length() > maxNameLength) {
                emailName = emailName.substring(0, maxNameLength);
            }

            email = emailName + suffix;
        }

        return new OAuth2UserInfo(
                OAuthProvider.KAKAO,
                providerUserId,
                email,
                nickname
        );
    }

    private String requiredString(Object value) {
        String result = optionalString(value);

        if (result == null) {
            throw new IllegalArgumentException(
                    "소셜 사용자 ID가 없습니다."
            );
        }

        return result;
    }

    private String optionalString(Object value) {
        if (value == null) {
            return null;
        }

        String result = value.toString();
        return result.isBlank() ? null : result;
    }

    private Map<String, Object> nestedMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }

        Map<String, Object> result = new java.util.HashMap<>();

        map.forEach((key, entry) -> {
            if (key instanceof String name) {
                result.put(name, entry);
            }
        });

        return result;
    }
}
