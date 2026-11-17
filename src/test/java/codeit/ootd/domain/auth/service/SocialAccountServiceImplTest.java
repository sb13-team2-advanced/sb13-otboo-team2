package codeit.ootd.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.entity.SocialAccount;
import codeit.ootd.domain.auth.exception.OAuthProviderAlreadyLinkedException;
import codeit.ootd.domain.auth.exception.SocialAccountAlreadyLinkedException;
import codeit.ootd.domain.auth.repository.SocialAccountRepository;
import codeit.ootd.domain.auth.service.impl.SocialAccountServiceImpl;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
    void 기존_회원이_소셜_계정을_정상적으로_연동한다() {
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
    void 이미_연동된_소셜_계정은_다른_사용자에게_연동할_수_없다() {
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
    void 이미_같은_제공자를_연동한_사용자는_추가_연동할_수_없다() {
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
}
