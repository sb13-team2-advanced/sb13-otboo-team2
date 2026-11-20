package codeit.ootd.domain.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.entity.SocialAccount;
import codeit.ootd.domain.auth.oauth2.OAuth2UserInfo;
import codeit.ootd.domain.auth.repository.SocialAccountRepository;
import codeit.ootd.domain.auth.service.SocialSignupService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "spring.security.oauth2.client.registration.google.scope=profile"
})
@ActiveProfiles("test")
class SocialSignupServiceIntegrationTest {

    @Autowired
    private SocialSignupService socialSignupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("소셜 계정 저장 실패 시 생성된 사용자도 롤백된다")
    void register_socialAccountConflict_rollsBackUser() {
        // given: 이미 연동된 소셜 계정
        String suffix = UUID.randomUUID().toString();
        String providerUserId = "google-" + suffix;
        String existingEmail = "existing-" + suffix + "@example.com";
        String newEmail = "new-" + suffix + "@example.com";

        User existingUser = userRepository.saveAndFlush(
                User.create(
                        "기존 사용자",
                        existingEmail,
                        passwordEncoder.encode("test-password")
                )
        );

        SocialAccount existingAccount =
                socialAccountRepository.saveAndFlush(
                        SocialAccount.create(
                                existingUser.getId(),
                                OAuthProvider.GOOGLE,
                                providerUserId
                        )
                );

        try {
            OAuth2UserInfo userInfo = new OAuth2UserInfo(
                    OAuthProvider.GOOGLE,
                    providerUserId,
                    newEmail,
                    "신규 사용자"
            );

            // when: 동일한 소셜 계정을 다른 사용자로 등록
            assertThatThrownBy(
                    () -> socialSignupService.register(userInfo)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // then: 신규 사용자는 DB에 남아 있으면 안 됨
            assertThat(userRepository.findByEmailIgnoreCase(newEmail))
                    .isEmpty();

            // 기존 소셜 계정은 그대로 유지
            assertThat(
                    socialAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GOOGLE,
                            providerUserId
                    )
            ).isPresent();

        } finally {
            // 테스트 데이터 정리
            socialAccountRepository.deleteById(existingAccount.getId());
            userRepository.deleteById(existingUser.getId());
        }
    }
}
