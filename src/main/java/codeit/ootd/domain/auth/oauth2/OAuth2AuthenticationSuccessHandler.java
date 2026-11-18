package codeit.ootd.domain.auth.oauth2;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.exception.UserNotFoundException;
import codeit.ootd.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2User principal = (OAuth2User) authentication.getPrincipal();

        String rawUserId = principal.getAttribute("otbooUserId");
        UUID userId = UUID.fromString(rawUserId);

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        SignInResult result =
                authService.issueTokensForAuthenticatedUser(user);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieFactory.create(result.refreshToken()).toString()
        );

        response.sendRedirect("/#/auth/login?oauth_success=true");
    }
}

