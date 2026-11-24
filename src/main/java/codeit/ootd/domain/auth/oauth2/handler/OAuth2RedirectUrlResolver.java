
package codeit.ootd.domain.auth.oauth2.handler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OAuth2RedirectUrlResolver {

    private final String baseUrl;

    public OAuth2RedirectUrlResolver(
            @Value("${OAUTH2_REDIRECT_BASE_URL:http://localhost:8080}")
            String baseUrl
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String resolve(String path) {
        return baseUrl + path;
    }
}
