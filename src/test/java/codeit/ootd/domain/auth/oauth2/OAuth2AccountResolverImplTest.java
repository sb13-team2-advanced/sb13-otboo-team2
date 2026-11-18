package codeit.ootd.domain.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.service.SocialAccountService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@ExtendWith(MockitoExtension.class)
class OAuth2AccountResolverImplTest {

    @Mock
    private SocialAccountService socialAccountService;

    @InjectMocks
    private OAuth2AccountResolverImpl resolver;

    @Test
    @DisplayName("연동된 소셜 계정으로 기존 사용자 ID를 조회한다")
    void resolve_user_id_success() {
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
    }

    @Test
    @DisplayName("연동되지 않은 소셜 계정은 로그인을 거부한다")
    void resolve_user_id_fails_when_account_not_linked() {
        // given
        OAuth2UserInfo userInfo = new OAuth2UserInfo(
                OAuthProvider.KAKAO,
                "kakao-123",
                null,
                "카카오 사용자"
        );

        when(socialAccountService.findUserId(
                OAuthProvider.KAKAO, "kakao-123"
        )).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resolver.resolveUserId(userInfo))
                .isInstanceOfSatisfying(
                        OAuth2AuthenticationException.class,
                        exception -> assertThat(
                                exception.getError().getErrorCode()
                        ).isEqualTo("user_not_exists")
                );
    }
}

