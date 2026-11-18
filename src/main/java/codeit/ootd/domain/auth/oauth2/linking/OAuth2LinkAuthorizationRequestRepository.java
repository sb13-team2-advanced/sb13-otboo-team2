package codeit.ootd.domain.auth.oauth2.linking;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LinkAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String LINK_TOKEN_ATTRIBUTE = "otboo_link_token";

    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(
            HttpServletRequest request
    ) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            delegate.saveAuthorizationRequest(null, request, response);
            return;
        }

        String linkToken = findLinkToken(request);

        if (linkToken != null) {
            authorizationRequest = OAuth2AuthorizationRequest
                    .from(authorizationRequest)
                    .attributes(attributes ->
                            attributes.put(LINK_TOKEN_ATTRIBUTE, linkToken)
                    )
                    .build();
        }

        delegate.saveAuthorizationRequest(
                authorizationRequest, request, response
        );
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return delegate.removeAuthorizationRequest(request, response);
    }

    private String findLinkToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie ->
                        "OAUTH2_LINK_TOKEN".equals(cookie.getName())
                )
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
