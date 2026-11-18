package codeit.ootd.domain.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import codeit.ootd.domain.auth.service.SocialAccountService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "MAIL_HOST=localhost",
        "MAIL_PORT=1025",
        "MAIL_USERNAME=test@example.com",
        "MAIL_PASSWORD=test-password"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2CallbackSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocialAccountService socialAccountService;

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
}

