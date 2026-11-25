package codeit.ootd.domain.auth.oauth2.handler;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.cookie.RefreshTokenCookieFactory;
import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.exception.OAuthProviderAlreadyLinkedException;
import codeit.ootd.domain.auth.exception.SocialAccountAlreadyLinkedException;
import codeit.ootd.domain.auth.oauth2.OAuth2AccountResolver;
import codeit.ootd.domain.auth.oauth2.OAuth2UserInfo;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequest;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequestService;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.auth.service.SocialAccountService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.exception.UserNotFoundException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.OtbooException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;
    private final OAuth2AccountResolver resolver;
    private final OAuth2LinkRequestService linkRequestService;
    private final SocialAccountService socialAccountService;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final OAuth2RedirectUrlResolver redirectUrlResolver;


    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuth2User principal = (OAuth2User) authentication.getPrincipal();

        Object linkToken = request.getAttribute("otboo_link_token");

        if (linkToken != null) {
            clearLinkCookie(response);
        }

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

            if (linkToken != null) {
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
                        redirectUrlResolver.resolve("/#/settings?link_success=true")
                );
                return;
            }

            // 일반 소셜 로그인
            OAuth2UserInfo userInfo = new OAuth2UserInfo(
                    provider,
                    providerUserId,
                    principal.getAttribute("otbooEmail"),
                    principal.getAttribute("otbooName")
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
                    redirectUrlResolver.resolve("/#/recommendations")
            );

        } catch (OAuth2AuthenticationException e) {
            failureHandler.onAuthenticationFailure(request, response, e);

        } catch (DuplicateEmailException e) {
            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new OAuth2AuthenticationException(
                            new OAuth2Error("email_already_exists"),
                            "이미 가입된 이메일입니다. 기존 계정으로 로그인한 후 소셜 계정을 연동해 주세요.",
                            e
                    )
            );
        } catch (SocialAccountAlreadyLinkedException e) {
            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new OAuth2AuthenticationException(
                            new OAuth2Error("social_account_already_linked"),
                            "이미 다른 계정에 연동된 소셜 계정입니다.",
                            e
                    )
            );
        } catch (OAuthProviderAlreadyLinkedException e) {
            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new OAuth2AuthenticationException(
                            new OAuth2Error("oauth_provider_already_linked"),
                            "이미 해당 소셜 로그인 제공자가 연동되어 있습니다.",
                            e
                    )
            );

        } catch (OtbooException e) {
            String errorCode = linkToken != null
                    ? "oauth_link_failed"
                    : "oauth_failed";

            log.error(
                    "OAuth2 사용자 처리 실패: exceptionType={}, errorCode={}",
                    e.getClass().getSimpleName(),
                    errorCode,
                    e
            );

            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new OAuth2AuthenticationException(
                            new OAuth2Error(errorCode),
                            "OAuth2 사용자 처리 실패",
                            e
                    )
            );
        }

    }

        private OAuthProvider resolveProvider (Object rawProvider){
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

        private void clearLinkCookie (HttpServletResponse response){
            ResponseCookie cookie = ResponseCookie
                    .from("OAUTH2_LINK_TOKEN", "")
                    .httpOnly(true)
                    .sameSite("Lax")
                    .path("/oauth2/authorization")
                    .maxAge(Duration.ZERO)
                    .build();

            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    cookie.toString()
            );
        }


    }

