package codeit.ootd.domain.auth.oauth2.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.cookie.RefreshTokenCookieFactory;
import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.oauth2.OAuth2AccountResolver;
import codeit.ootd.domain.auth.oauth2.OAuth2UserInfo;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequest;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequestService;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.auth.service.SocialAccountService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.OtbooException;
import java.time.Instant;
import java.util.List;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private OAuth2AccountResolver resolver;

    @Mock
    private OAuth2LinkRequestService linkRequestService;

    @Mock
    private SocialAccountService socialAccountService;

    @Mock
    private OAuth2AuthenticationFailureHandler failureHandler;

    private OAuth2AuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationSuccessHandler(
                userRepository,
                authService,
                new RefreshTokenCookieFactory(true),
                resolver,
                linkRequestService,
                socialAccountService,
                failureHandler
        );
    }

    @Test
    @DisplayName("일반 소셜 로그인 시 기존 사용자에게 JWT를 발급한다")
    void success_normalLogin_issuesTokens() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        SignInResult result = mock(SignInResult.class);

        when(resolver.resolveUserId(any(OAuth2UserInfo.class)))
                .thenReturn(userId);
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(authService.issueTokensForAuthenticatedUser(user))
                .thenReturn(result);
        when(result.refreshToken()).thenReturn("refresh-token");

        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication("GOOGLE", "google-123")
        );

        verify(resolver).resolveUserId(
                new OAuth2UserInfo(
                        OAuthProvider.GOOGLE,
                        "google-123",
                        null,
                        null
                )
        );
        verify(authService).issueTokensForAuthenticatedUser(user);
        verifyNoInteractions(linkRequestService, socialAccountService);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("REFRESH_TOKEN=refresh-token")
                .contains("Path=/api/auth")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/#/auth/login?oauth_success=true");
    }

    @Test
    @DisplayName("정상 계정 연동 시 소셜 계정을 연결하고 JWT는 발급하지 않는다")
    void success_linkAccount_doesNotIssueTokens() throws Exception {
        UUID userId = UUID.randomUUID();

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        request.setAttribute("otboo_link_token", "link-token");

        OAuth2LinkRequest linkRequest = new OAuth2LinkRequest(
                userId,
                OAuthProvider.GOOGLE,
                Instant.now().plusSeconds(300)
        );

        when(linkRequestService.consume(
                "link-token",
                OAuthProvider.GOOGLE
        )).thenReturn(linkRequest);

        handler.onAuthenticationSuccess(
                request,
                response,
                authentication("GOOGLE", "google-123")
        );

        verify(linkRequestService).consume(
                "link-token",
                OAuthProvider.GOOGLE
        );
        verify(socialAccountService).linkAccount(
                userId,
                OAuthProvider.GOOGLE,
                "google-123"
        );

        verifyNoInteractions(resolver, userRepository, authService);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/#/auth/login?link_success=true");
    }

    @Test
    @DisplayName("미연동 계정의 일반 로그인은 거부한다")
    void success_unlinkedAccount_failsLogin() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        when(resolver.resolveUserId(any(OAuth2UserInfo.class)))
                .thenThrow(
                        new OAuth2AuthenticationException("user_not_exists")
                );

        handler.onAuthenticationSuccess(
                request,
                response,
                authentication("KAKAO", "123456789")
        );

        verify(failureHandler).onAuthenticationFailure(
                eq(request),
                eq(response),
                argThat(exception ->
                        exception instanceof OAuth2AuthenticationException oauthException
                                && "user_not_exists".equals(
                                oauthException.getError().getErrorCode()
                        )
                )
        );


        verifyNoInteractions(
                userRepository,
                authService,
                linkRequestService,
                socialAccountService
        );
    }

    @Test
    @DisplayName("유효하지 않은 연동 토큰은 소셜 계정을 연결하지 않는다")
    void success_invalidLinkToken_doesNotLinkAccount() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        request.setAttribute("otboo_link_token", "invalid-token");

        OtbooException error = mock(OtbooException.class);

        when(linkRequestService.consume(
                "invalid-token",
                OAuthProvider.GOOGLE
        )).thenThrow(error);

        handler.onAuthenticationSuccess(
                request,
                response,
                authentication("GOOGLE", "google-123")
        );

        verify(failureHandler).onAuthenticationFailure(
                eq(request),
                eq(response),
                argThat(exception ->
                        exception instanceof OAuth2AuthenticationException oauthException
                                && "oauth_link_failed".equals(
                                oauthException.getError().getErrorCode()
                        )
                )
        );

        verifyNoInteractions(
                socialAccountService,
                authService,
                resolver,
                userRepository
        );
    }

    private Authentication authentication(
            String provider,
            String providerUserId
    ) {
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "otbooProvider", provider,
                        "otbooProviderUserId", providerUserId
                ),
                "otbooProviderUserId"
        );

        return new UsernamePasswordAuthenticationToken(
                oauth2User,
                null,
                oauth2User.getAuthorities()
        );
    }

    @Test
    @DisplayName("잘못된 OAuth2 제공자는 인증 실패로 처리한다")
    void success_invalidProvider_failsAuthentication() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                authentication("INVALID", "provider-123")
        );

        verify(failureHandler).onAuthenticationFailure(
                eq(request),
                eq(response),
                argThat(exception ->
                        exception instanceof OAuth2AuthenticationException oauthException
                                && "unsupported_oauth_provider".equals(
                                oauthException.getError().getErrorCode()
                        )
                )
        );

        verifyNoInteractions(
                resolver,
                linkRequestService,
                socialAccountService,
                authService
        );
    }

    @Test
    @DisplayName("소셜 계정 연동 성공 시 연동 요청 쿠키를 삭제한다")
    void success_linkAccount_clearsLinkCookie() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        request.setAttribute("otboo_link_token", "link-token");

        when(linkRequestService.consume(
                "link-token",
                OAuthProvider.GOOGLE
        )).thenReturn(new OAuth2LinkRequest(
                userId,
                OAuthProvider.GOOGLE,
                Instant.now().plusSeconds(300)
        ));

        // when
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication("GOOGLE", "google-123")
        );

        // then
        verify(socialAccountService).linkAccount(
                userId,
                OAuthProvider.GOOGLE,
                "google-123"
        );

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("OAUTH2_LINK_TOKEN=")
                        .contains("Path=/oauth2/authorization")
                        .contains("Max-Age=0")
                );
    }

    @Test
    @DisplayName("소셜 계정 연동 실패 시에도 연동 요청 쿠키를 삭제한다")
    void success_invalidLinkToken_clearsLinkCookie() throws Exception {
        // given
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        request.setAttribute("otboo_link_token", "invalid-token");

        when(linkRequestService.consume(
                "invalid-token",
                OAuthProvider.GOOGLE
        )).thenThrow(mock(OtbooException.class));

        // when
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication("GOOGLE", "google-123")
        );

        // then
        verifyNoInteractions(socialAccountService);

        verify(failureHandler).onAuthenticationFailure(
                eq(request),
                eq(response),
                any()
        );

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("OAUTH2_LINK_TOKEN=")
                        .contains("Path=/oauth2/authorization")
                        .contains("Max-Age=0")
                );
    }


}
