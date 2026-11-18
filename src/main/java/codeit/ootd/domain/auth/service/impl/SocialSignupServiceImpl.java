package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.entity.SocialAccount;
import codeit.ootd.domain.auth.oauth2.OAuth2UserInfo;
import codeit.ootd.domain.auth.repository.SocialAccountRepository;
import codeit.ootd.domain.auth.service.SocialSignupService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.Objects;
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
        Objects.requireNonNull(
                userInfo,
                "소셜 사용자 정보는 필수입니다."
        );

        String email = userInfo.email();

        // 기존 이메일과 중복되면 자동 회원가입 거부
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }

        String randomPassword =
                UUID.randomUUID().toString() + UUID.randomUUID();

        String encodedPassword =
                passwordEncoder.encode(randomPassword);

        User user = User.create(
                userInfo.name(),
                email,
                encodedPassword
        );

        User savedUser = userRepository.saveAndFlush(user);

        SocialAccount socialAccount = SocialAccount.create(
                savedUser.getId(),
                userInfo.provider(),
                userInfo.providerUserId()
        );

        socialAccountRepository.save(socialAccount);

        return savedUser.getId();
    }

}
