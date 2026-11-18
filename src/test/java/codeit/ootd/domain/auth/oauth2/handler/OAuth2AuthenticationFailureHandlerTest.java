package codeit.ootd.domain.auth.oauth2.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class OAuth2AuthenticationFailureHandlerTest {

    private final OAuth2AuthenticationFailureHandler handler =
            new OAuth2AuthenticationFailureHandler();

    @Test
    @DisplayName("미연동 소셜 계정은 로그인 화면으로 오류 코드와 함께 이동한다")
    void authenticationFailure_unlinkedAccount_redirectsToLogin()
            throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthenticationException exception =
                new OAuth2AuthenticationException("user_not_exists");

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/#/auth/login?error=oauth_failed"
                        + "&error_message=user_not_exists"
        );
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("OAUTH2_LINK_TOKEN=")
                        .contains("Path=/oauth2/authorization")
                        .contains("Max-Age=0")
                );

    }

    @Test
    @DisplayName("일반 인증 오류는 내부 정보를 노출하지 않고 로그인 화면으로 이동한다")
    void authenticationFailure_unknownError_redirectsToLogin()
            throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        BadCredentialsException exception =
                new BadCredentialsException("internal authentication details");

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/#/auth/login?error=oauth_failed"
                        + "&error_message=oauth_failed"
        );
    }

}
