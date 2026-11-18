package codeit.ootd.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.entity.SocialAccount;
import codeit.ootd.domain.auth.oauth2.OAuth2UserInfo;
import codeit.ootd.domain.auth.repository.SocialAccountRepository;
import codeit.ootd.domain.auth.service.impl.SocialSignupServiceImpl;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SocialSignupServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SocialSignupServiceImpl socialSignupService;

    @Test
    @DisplayName("최초 Google 로그인 시 사용자와 소셜 계정을 생성한다")
    void register_google_createsUserAndSocialAccount() {
        // given
        OAuth2UserInfo userInfo = new OAuth2UserInfo(
                OAuthProvider.GOOGLE,
                "google-123",
                "google@example.com",
                "구글 사용자"
        );

        UUID userId = UUID.randomUUID();

        when(passwordEncoder.encode(any(String.class)))
                .thenReturn("encoded-password");

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "id", userId);
                    return user;
                });

        // when
        UUID result = socialSignupService.register(userInfo);

        // then
        assertThat(result).isEqualTo(userId);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).saveAndFlush(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail())
                .isEqualTo("google@example.com");
        assertThat(savedUser.getName())
                .isEqualTo("구글 사용자");
        assertThat(savedUser.getPassword())
                .isEqualTo("encoded-password");
        assertThat(savedUser.getRole())
                .isEqualTo(UserRole.USER);
        assertThat(savedUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.isLocked()).isFalse();

        ArgumentCaptor<SocialAccount> accountCaptor =
                ArgumentCaptor.forClass(SocialAccount.class);

        verify(socialAccountRepository)
                .save(accountCaptor.capture());

        SocialAccount savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getUserId()).isEqualTo(userId);
        assertThat(savedAccount.getProvider())
                .isEqualTo(OAuthProvider.GOOGLE);
        assertThat(savedAccount.getProviderUserId())
                .isEqualTo("google-123");
    }
}
