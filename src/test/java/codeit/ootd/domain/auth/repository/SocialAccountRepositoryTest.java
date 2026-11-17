package codeit.ootd.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.entity.SocialAccount;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(SocialAccountRepositoryTest.JpaAuditingTestConfig.class)
class SocialAccountRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class JpaAuditingTestConfig {
    }

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 소셜_제공자와_사용자_ID로_연결된_계정을_조회한다() {
        User user = userRepository.save(
                User.create("테스터", "social@example.com", "encoded-password")
        );

        SocialAccount account = socialAccountRepository.save(
                SocialAccount.create(
                        user.getId(),
                        OAuthProvider.GOOGLE,
                        "google-123"
                )
        );

        assertThat(
                socialAccountRepository.findByProviderAndProviderUserId(
                        OAuthProvider.GOOGLE,
                        "google-123"
                )
        ).contains(account);

        assertThat(
                socialAccountRepository.findByProviderAndProviderUserId(
                        OAuthProvider.KAKAO,
                        "google-123"
                )
        ).isEmpty();
    }

    @Test
    void 동일한_소셜_계정을_다른_사용자에게_중복_연결할_수_없다() {
        User firstUser = userRepository.save(
                User.create("사용자1", "first@example.com", "encoded-password")
        );

        User secondUser = userRepository.save(
                User.create("사용자2", "second@example.com", "encoded-password")
        );

        socialAccountRepository.saveAndFlush(
                SocialAccount.create(
                        firstUser.getId(),
                        OAuthProvider.GOOGLE,
                        "google-123"
                )
        );

        assertThatThrownBy(() ->
                socialAccountRepository.saveAndFlush(
                        SocialAccount.create(
                                secondUser.getId(),
                                OAuthProvider.GOOGLE,
                                "google-123"
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
