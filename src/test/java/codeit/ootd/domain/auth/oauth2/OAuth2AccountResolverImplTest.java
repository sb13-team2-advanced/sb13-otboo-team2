package codeit.ootd.domain.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.service.SocialAccountService;
import codeit.ootd.domain.auth.service.SocialSignupService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuth2AccountResolverImplTest {

    private SocialAccountService socialAccountService;
    private SocialSignupService socialSignupService;
    private OAuth2AccountResolverImpl resolver;

    @BeforeEach
    void setUp() {
        socialAccountService = mock(SocialAccountService.class);
        socialSignupService = mock(SocialSignupService.class);

        resolver = new OAuth2AccountResolverImpl(
                socialAccountService,
                socialSignupService
        );
    }

    @Test
    @DisplayName("이미 연동된 소셜 계정은 기존 사용자 ID를 반환한다")
    void resolve_existingAccount_returnsUserId() {
        // given
        UUID userId = UUID.randomUUID();

        OAuth2UserInfo userInfo = new OAuth2UserInfo(
                OAuthProvider.GOOGLE,
                "google-123",
                "google@example.com",
                "구글 사용자"
        );

        when(socialAccountService.findUserId(
                OAuthProvider.GOOGLE, "google-123"
        )).thenReturn(Optional.of(userId));

        // when
        UUID result = resolver.resolveUserId(userInfo);

        // then
        assertThat(result).isEqualTo(userId);
        verifyNoInteractions(socialSignupService);
    }

    @Test
    @DisplayName("미연동 소셜 계정은 자동 회원가입 후 신규 사용자 ID를 반환한다")
    void resolve_unlinkedAccount_registersNewUser() {
        // given
        UUID newUserId = UUID.randomUUID();

        OAuth2UserInfo userInfo = new OAuth2UserInfo(
                OAuthProvider.GOOGLE,
                "google-new",
                "new@example.com",
                "신규 사용자"
        );

        when(socialAccountService.findUserId(
                OAuthProvider.GOOGLE, "google-new"
        )).thenReturn(Optional.empty());

        when(socialSignupService.register(userInfo))
                .thenReturn(newUserId);

        // when
        UUID result = resolver.resolveUserId(userInfo);

        // then
        assertThat(result).isEqualTo(newUserId);
        verify(socialSignupService).register(userInfo);
    }
}
