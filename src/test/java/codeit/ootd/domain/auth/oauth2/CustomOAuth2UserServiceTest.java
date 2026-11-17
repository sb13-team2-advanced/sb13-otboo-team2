package codeit.ootd.domain.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private DefaultOAuth2UserService delegate;

    @Mock
    private OAuth2UserInfoExtractor extractor;

    @Mock
    private OAuth2AccountResolver resolver;

    @InjectMocks
    private CustomOAuth2UserService userService;

    @Test
    @DisplayName("Google 인증 성공 시 연동된 OTBOO 사용자 ID를 반환한다")
    void load_user_success() {
        // given
        UUID userId = UUID.randomUUID();

        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);

        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("google");

        Map<String, Object> attributes = Map.of(
                "sub", "google-123",
                "email", "google@example.com",
                "name", "구글 사용자"
        );

        OAuth2User providerUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                attributes,
                "sub"
        );

        OAuth2UserInfo userInfo = new OAuth2UserInfo(
                OAuthProvider.GOOGLE,
                "google-123",
                "google@example.com",
                "구글 사용자"
        );

        when(delegate.loadUser(request)).thenReturn(providerUser);
        when(extractor.extract(OAuthProvider.GOOGLE, attributes))
                .thenReturn(userInfo);
        when(resolver.resolveUserId(userInfo)).thenReturn(userId);

        // when
        OAuth2User result = userService.loadUser(request);

        // then
        String otbooUserId = result.getAttribute("otbooUserId");
        assertThat(otbooUserId).isEqualTo(userId.toString());

        verify(delegate).loadUser(request);
        verify(resolver).resolveUserId(userInfo);
    }

    @Test
    @DisplayName("연동되지 않은 Kakao 계정은 OAuth2 로그인을 거부한다")
    void load_user_fails_when_account_not_linked() {
        // given
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);

        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("kakao");

        Map<String, Object> attributes = Map.of(
                "id", 123456789L
        );

        OAuth2User providerUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                attributes,
                "id"
        );

        OAuth2UserInfo userInfo = new OAuth2UserInfo(
                OAuthProvider.KAKAO,
                "123456789",
                null,
                null
        );

        when(delegate.loadUser(request)).thenReturn(providerUser);
        when(extractor.extract(OAuthProvider.KAKAO, attributes))
                .thenReturn(userInfo);

        when(resolver.resolveUserId(userInfo)).thenThrow(
                new OAuth2AuthenticationException("user_not_exists")
        );

        // when & then
        assertThatThrownBy(() -> userService.loadUser(request))
                .isInstanceOfSatisfying(
                        OAuth2AuthenticationException.class,
                        exception -> assertThat(
                                exception.getError().getErrorCode()
                        ).isEqualTo("user_not_exists")
                );
    }
}
