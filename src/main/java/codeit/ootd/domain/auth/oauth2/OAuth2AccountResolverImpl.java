package codeit.ootd.domain.auth.oauth2;

import codeit.ootd.domain.auth.service.SocialAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2AccountResolverImpl implements OAuth2AccountResolver {

    private final SocialAccountService socialAccountService;

    @Override
    public UUID resolveUserId(OAuth2UserInfo userInfo) {
        Objects.requireNonNull(userInfo, "소셜 사용자 정보는 필수입니다.");

        return socialAccountService.findUserId(
                userInfo.provider(),
                userInfo.providerUserId()
        ).orElseThrow(() -> new OAuth2AuthenticationException(
                new OAuth2Error("user_not_exists"),
                "연동되지 않은 소셜 계정입니다."
        ));
    }
}
