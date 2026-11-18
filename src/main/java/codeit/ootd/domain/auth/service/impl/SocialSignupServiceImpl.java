package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.oauth2.OAuth2UserInfo;
import codeit.ootd.domain.auth.repository.SocialAccountRepository;
import codeit.ootd.domain.auth.service.SocialSignupService;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialSignupServiceImpl implements SocialSignupService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UUID register(OAuth2UserInfo userInfo) {
        throw new UnsupportedOperationException(
                "소셜 자동 회원가입 미구현"
        );
    }
}
