package codeit.ootd.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.entity.SocialAccount;
import codeit.ootd.domain.auth.exception.OAuthProviderAlreadyLinkedException;
import codeit.ootd.domain.auth.exception.SocialAccountAlreadyLinkedException;
import codeit.ootd.domain.auth.repository.SocialAccountRepository;
import codeit.ootd.domain.auth.service.impl.SocialAccountServiceImpl;
import codeit.ootd.domain.user.exception.UserNotFoundException;
import codeit.ootd.domain.user.repository.UserRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceImplTest {

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SocialAccountServiceImpl socialAccountService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("소셜 계정으로 연결된 사용자 ID를 조회한다")
    void find_user_id_success() {
        // given
        SocialAccount account = SocialAccount.create(
                userId, OAuthProvider.GOOGLE, "google-123"
        );

        when(socialAccountRepository.findByProviderAndProviderUserId(
                OAuthProvider.GOOGLE, "google-123"
        )).thenReturn(Optional.of(account));

        // when
        Optional<UUID> result = socialAccountService.findUserId(
                OAuthProvider.GOOGLE, "google-123"
        );

        // then
        assertThat(result).contains(userId);
    }

    @Test
    @DisplayName("기존 회원이 소셜 계정을 정상적으로 연동한다")
    void link_account_success() {
        // given
        when(userRepository.existsById(userId)).thenReturn(true);

        when(socialAccountRepository.findByProviderAndProviderUserId(
                OAuthProvider.GOOGLE, "google-123"
        )).thenReturn(Optional.empty());

        when(socialAccountRepository.existsByUserIdAndProvider(
                userId, OAuthProvider.GOOGLE
        )).thenReturn(false);

        // when
        socialAccountService.linkAccount(
                userId, OAuthProvider.GOOGLE, "google-123"
        );

        // then
        ArgumentCaptor<SocialAccount> captor =
                ArgumentCaptor.forClass(SocialAccount.class);

        verify(socialAccountRepository).save(captor.capture());

        SocialAccount saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(saved.getProviderUserId()).isEqualTo("google-123");
    }

    @Test
    @DisplayName("이미 다른 사용자에게 연동된 소셜 계정은 연동할 수 없다")
    void link_account_fails_when_social_account_already_linked() {
        // given
        when(userRepository.existsById(userId)).thenReturn(true);

        when(socialAccountRepository.findByProviderAndProviderUserId(
                OAuthProvider.GOOGLE, "google-123"
        )).thenReturn(Optional.of(
                SocialAccount.create(
                        UUID.randomUUID(),
                        OAuthProvider.GOOGLE,
                        "google-123"
                )
        ));

        // when & then
        assertThatThrownBy(() ->
                socialAccountService.linkAccount(
                        userId, OAuthProvider.GOOGLE, "google-123"
                )
        ).isInstanceOf(SocialAccountAlreadyLinkedException.class);

        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("동일 제공자의 계정이 이미 연동되어 있으면 추가 연동할 수 없다")
    void link_account_fails_when_provider_already_linked() {
        // given
        when(userRepository.existsById(userId)).thenReturn(true);

        when(socialAccountRepository.findByProviderAndProviderUserId(
                OAuthProvider.GOOGLE, "another-google"
        )).thenReturn(Optional.empty());

        when(socialAccountRepository.existsByUserIdAndProvider(
                userId, OAuthProvider.GOOGLE
        )).thenReturn(true);

        // when & then
        assertThatThrownBy(() ->
                socialAccountService.linkAccount(
                        userId, OAuthProvider.GOOGLE, "another-google"
                )
        ).isInstanceOf(OAuthProviderAlreadyLinkedException.class);

        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("소셜 사용자 ID가 공백이면 연동할 수 없다")
    void link_account_fails_when_provider_user_id_blank() {
        // when & then
        assertThatThrownBy(() ->
                socialAccountService.linkAccount(
                        userId, OAuthProvider.GOOGLE, " "
                )
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
                userRepository,
                socialAccountRepository
        );
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 소셜 계정을 연동할 수 없다")
    void link_account_fails_when_user_not_found() {
        // given
        when(userRepository.existsById(userId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() ->
                socialAccountService.linkAccount(
                        userId, OAuthProvider.GOOGLE, "google-123"
                )
        ).isInstanceOf(UserNotFoundException.class);

        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Google과 Kakao의 계정 연동 여부를 조회한다")
    void getLinkedProviders_returnsProviderStatus() {
        // given
        UUID userId = UUID.randomUUID();

        given(socialAccountRepository.existsByUserIdAndProvider(
                userId, OAuthProvider.GOOGLE
        )).willReturn(true);

        given(socialAccountRepository.existsByUserIdAndProvider(
                userId, OAuthProvider.KAKAO
        )).willReturn(false);

        // when
        Map<String, Boolean> result =
                socialAccountService.getLinkedProviders(userId);

        // then
        assertThat(result)
                .containsEntry("google", true)
                .containsEntry("kakao", false)
                .hasSize(2);
    }

}
