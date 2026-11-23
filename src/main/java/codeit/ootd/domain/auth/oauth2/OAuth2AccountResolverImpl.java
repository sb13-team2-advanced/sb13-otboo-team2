package codeit.ootd.domain.auth.oauth2;

import codeit.ootd.domain.auth.service.SocialAccountService;
import codeit.ootd.domain.auth.service.SocialSignupService;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuth2AccountResolverImpl implements OAuth2AccountResolver {

    private final SocialAccountService socialAccountService;
    private final SocialSignupService socialSignupService;

    @Override
    public UUID resolveUserId(OAuth2UserInfo userInfo) {
        Objects.requireNonNull(
                userInfo,
                "소셜 사용자 정보는 필수입니다."
        );

        return socialAccountService.findUserId(
                userInfo.provider(),
                userInfo.providerUserId()
        ).orElseGet(() -> socialSignupService.register(userInfo));
    }
}
