package codeit.ootd.domain.auth.oauth2.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AuthenticationFailureHandler
        implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        String errorMessage = "oauth_failed";

        if (exception instanceof OAuth2AuthenticationException oauthException
                && "user_not_exists".equals(
                oauthException.getError().getErrorCode()
        )) {
            errorMessage = "user_not_exists";
        }

        response.sendRedirect(
                "/#/auth/login?error=oauth_failed"
                        + "&error_message=" + errorMessage
        );
    }
}
