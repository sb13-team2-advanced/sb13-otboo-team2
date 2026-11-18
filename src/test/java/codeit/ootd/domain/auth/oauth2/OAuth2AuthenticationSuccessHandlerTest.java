package codeit.ootd.domain.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    private OAuth2AuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationSuccessHandler(
                userRepository,
                authService,
                new RefreshTokenCookieFactory(true)
        );
    }

    @Test
    @DisplayName("OAuth2 인증 성공 시 기존 사용자에게 토큰을 발급하고 쿠키를 설정한다")
    void success_issuesTokensAndSetsRefreshCookie() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        SignInResult result = mock(SignInResult.class);

        OAuth2User oauth2User = new DefaultOAuth2User(
                java.util.List.of(
                        new SimpleGrantedAuthority("ROLE_USER")
                ),
                Map.of("otbooUserId", userId.toString()),
                "otbooUserId"
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        oauth2User,
                        null,
                        oauth2User.getAuthorities()
                );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(authService.issueTokensForAuthenticatedUser(user))
                .thenReturn(result);
        when(result.refreshToken()).thenReturn("refresh-token");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication
        );

        // then
        verify(authService)
                .issueTokensForAuthenticatedUser(user);

        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);

        assertThat(cookie)
                .contains("REFRESH_TOKEN=refresh-token")
                .contains("Path=/api/auth")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/#/auth/login?oauth_success=true");
    }
}
