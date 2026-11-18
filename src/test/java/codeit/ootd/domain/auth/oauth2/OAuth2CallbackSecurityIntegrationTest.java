package codeit.ootd.domain.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequest;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequestService;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.auth.service.SocialAccountService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "spring.security.oauth2.client.registration.google.scope=profile",
        "MAIL_HOST=localhost",
        "MAIL_PORT=1025",
        "MAIL_USERNAME=test@example.com",
        "MAIL_PASSWORD=test-password"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2CallbackSecurityIntegrationTest.TokenResponseTestConfig.class)
class OAuth2CallbackSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocialAccountService socialAccountService;

    @MockitoBean
    private DefaultOAuth2UserService delegate;

    @MockitoBean
    private OAuth2LinkRequestService linkRequestService;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("변조된 OAuth2 state로 콜백 요청 시 계정 연동을 차단한다")
    void callback_invalidState_doesNotLinkAccount() throws Exception {
        // given: 연동 쿠키와 함께 Google 인증 시작
        var start = mockMvc.perform(
                get("/oauth2/authorization/google")
                        .cookie(new Cookie(
                                "OAUTH2_LINK_TOKEN",
                                "test-link-token"
                        ))
        ).andReturn();

        assertThat(start.getResponse().getStatus()).isEqualTo(302);

        MockHttpSession session =
                (MockHttpSession) start.getRequest().getSession(false);

        assertThat(session).isNotNull();

        // when: 저장된 state와 다른 값으로 콜백 호출
        var callback = mockMvc.perform(
                get("/login/oauth2/code/google")
                        .session(session)
                        .param("code", "fake-code")
                        .param("state", "tampered-state")
        ).andReturn();

        // then
        assertThat(callback.getResponse().getStatus()).isEqualTo(302);

        assertThat(callback.getResponse().getRedirectedUrl())
                .isEqualTo(
                        "/#/auth/login?error=oauth_failed"
                                + "&error_message=oauth_failed"
                );

        verifyNoInteractions(socialAccountService);
    }

    @Test
    @DisplayName("정상 OAuth2 콜백은 기존 사용자에게 소셜 계정을 연동한다")
    void callback_validState_linksSocialAccount() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        var start = mockMvc.perform(
                get("/oauth2/authorization/google")
                        .cookie(new Cookie(
                                "OAUTH2_LINK_TOKEN",
                                "test-link-token"
                        ))
        ).andReturn();

        assertThat(start.getResponse().getStatus()).isEqualTo(302);

        MockHttpSession session =
                (MockHttpSession) start.getRequest().getSession(false);

        assertThat(session).isNotNull();

        String encodedState = UriComponentsBuilder
                .fromUriString(start.getResponse().getRedirectedUrl())
                .build()
                .getQueryParams()
                .getFirst("state");

        assertThat(encodedState).isNotNull();

        String state = URLDecoder.decode(
                encodedState,
                StandardCharsets.UTF_8
        );
        assertThat(state).isNotBlank();


        OAuth2User providerUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of(
                        "sub", "google-123",
                        "email", "google@example.com",
                        "name", "테스트 사용자"
                ),
                "sub"
        );

        when(delegate.loadUser(any(OAuth2UserRequest.class)))
                .thenReturn(providerUser);

        when(linkRequestService.consume(
                "test-link-token",
                OAuthProvider.GOOGLE
        )).thenReturn(new OAuth2LinkRequest(
                userId,
                OAuthProvider.GOOGLE,
                Instant.now().plusSeconds(300)
        ));

        // when
        var callback = mockMvc.perform(
                get("/login/oauth2/code/google")
                        .session(session)
                        .param("code", "test-authorization-code")
                        .param("state", state)
        ).andReturn();

        // then
        assertThat(callback.getResponse().getStatus()).isEqualTo(302);

        assertThat(callback.getResponse().getRedirectedUrl())
                .isEqualTo("/#/auth/login?link_success=true");

        verify(linkRequestService).consume(
                "test-link-token",
                OAuthProvider.GOOGLE
        );

        verify(socialAccountService).linkAccount(
                userId,
                OAuthProvider.GOOGLE,
                "google-123"
        );

        verifyNoInteractions(authService);

        assertThat(callback.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("OAUTH2_LINK_TOKEN=")
                        .contains("Max-Age=0")
                );

        assertThat(callback.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("REFRESH_TOKEN="));
    }

    @TestConfiguration
    static class TokenResponseTestConfig {

        @Bean
        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
        tokenResponseClient() {
            return request ->
                    OAuth2AccessTokenResponse.withToken("test-access-token")
                            .tokenType(OAuth2AccessToken.TokenType.BEARER)
                            .expiresIn(3600)
                            .build();
        }
    }


}

