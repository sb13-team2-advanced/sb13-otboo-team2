package codeit.ootd.domain.auth.oauth2.linking;

import static org.assertj.core.api.Assertions.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class OAuth2LinkAuthorizationRequestRepositoryTest {

    private final OAuth2LinkAuthorizationRequestRepository repository =
            new OAuth2LinkAuthorizationRequestRepository();

    private OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("test-client")
                .redirectUri("http://localhost/login/oauth2/code/google")
                .state("test-state")
                .build();
    }

    @Test
    void save_linkRequest_bindsTokenToState() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        request.setCookies(new Cookie("OAUTH2_LINK_TOKEN", "link-token"));

        repository.saveAuthorizationRequest(
                authorizationRequest(), request, response
        );

        request.setParameter("state", "test-state");

        var saved = repository.loadAuthorizationRequest(request);

        assertThat(saved).isNotNull();
        assertThat(saved.getAttribute("otboo_link_token"))
                .isEqualTo("link-token");
    }

    @Test
    void save_normalLogin_doesNotAddLinkToken() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(
                authorizationRequest(), request, response
        );

        request.setParameter("state", "test-state");

        var saved = repository.loadAuthorizationRequest(request);

        assertThat(saved).isNotNull();
        assertThat(saved.getAttribute("otboo_link_token")).isNull();
    }

    @Test
    void load_wrongState_returnsNull() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        request.setCookies(new Cookie("OAUTH2_LINK_TOKEN", "link-token"));

        repository.saveAuthorizationRequest(
                authorizationRequest(), request, response
        );

        request.setParameter("state", "wrong-state");

        assertThat(repository.loadAuthorizationRequest(request))
                .isNull();
    }
}
