package codeit.ootd.domain.auth.oauth2.handler;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.cookie.RefreshTokenCookieFactory;
import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.oauth2.OAuth2AccountResolver;
import codeit.ootd.domain.auth.oauth2.OAuth2UserInfo;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequest;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequestService;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.auth.service.SocialAccountService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.exception.UserNotFoundException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.OtbooException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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
    private final OAuth2AccountResolver resolver;
    private final OAuth2LinkRequestService linkRequestService;
    private final SocialAccountService socialAccountService;
    private final OAuth2AuthenticationFailureHandler failureHandler;


    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException{

        OAuth2User principal = (OAuth2User) authentication.getPrincipal();

        try {
            OAuthProvider provider = resolveProvider(
                    principal.getAttribute("otbooProvider")
            );
            String providerUserId =
                    principal.getAttribute("otbooProviderUserId");

            if (providerUserId == null || providerUserId.isBlank()) {
                throw new OAuth2AuthenticationException(
                        "invalid_oauth_user"
                );
            }

            Object linkToken = request.getAttribute("otboo_link_token");

            if (linkToken != null) {
                // 소셜 계정 연동
                if (!(linkToken instanceof String token)) {
                    throw new OAuth2AuthenticationException(
                            "invalid_link_request"
                    );
                }

                OAuth2LinkRequest linkRequest =
                        linkRequestService.consume(token, provider);

                socialAccountService.linkAccount(
                        linkRequest.userId(),
                        provider,
                        providerUserId
                );

                response.sendRedirect(
                        "/#/auth/login?link_success=true"
                );
                return;
            }

            // 일반 소셜 로그인
            OAuth2UserInfo userInfo = new OAuth2UserInfo(
                    provider,
                    providerUserId,
                    null,
                    null
            );

            UUID userId = resolver.resolveUserId(userInfo);

            User user = userRepository.findById(userId)
                    .orElseThrow(UserNotFoundException::new);

            SignInResult result =
                    authService.issueTokensForAuthenticatedUser(user);

            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    cookieFactory.create(result.refreshToken()).toString()
            );

            response.sendRedirect(
                    "/#/auth/login?oauth_success=true"
            );

        } catch (OAuth2AuthenticationException e) {
            failureHandler.onAuthenticationFailure(request, response, e);
        } catch (OtbooException e) {
            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new OAuth2AuthenticationException(
                            new OAuth2Error("oauth_link_failed"),
                            e.getMessage()
                    )
            );
        }
    }

    private OAuthProvider resolveProvider(Object rawProvider) {
        if (!(rawProvider instanceof String providerName)) {
            throw new OAuth2AuthenticationException(
                    "unsupported_oauth_provider"
            );
        }

        try {
            return OAuthProvider.valueOf(providerName);
        } catch (IllegalArgumentException e) {
            throw new OAuth2AuthenticationException(
                    "unsupported_oauth_provider"
            );
        }
    }


}

