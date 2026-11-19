package codeit.ootd.domain.auth.oauth2.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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

        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String errorCode = oauthException.getError().getErrorCode();

            if ("user_not_exists".equals(errorCode)
                    || "oauth_link_failed".equals(errorCode)|| "email_already_exists".equals(errorCode)
                    || "email_required".equals(errorCode)) {
                errorMessage = errorCode;
            }
        }

        ResponseCookie cookie = ResponseCookie
                .from("OAUTH2_LINK_TOKEN", "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/oauth2/authorization")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        response.sendRedirect(
                "/#/auth/login?error=oauth_failed"
                        + "&error_message=" + errorMessage
        );
    }
}
