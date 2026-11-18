package codeit.ootd.domain.auth.oauth2.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import java.util.UUID;

import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class OAuth2LinkControllerTest {

    private OAuth2LinkRequestService linkRequestService;
    private OAuth2LinkController controller;

    @BeforeEach
    void setUp() {
        linkRequestService = mock(OAuth2LinkRequestService.class);
        controller = new OAuth2LinkController(
                linkRequestService,
                true
        );
    }

    @Test
    @DisplayName("Google 연동 요청 시 인증 사용자로 요청을 생성한다")
    void startLink_google_success() {
        // given
        UUID userId = UUID.randomUUID();

        when(linkRequestService.create(userId, OAuthProvider.GOOGLE))
                .thenReturn("link-token");

        // when
        ResponseEntity<OAuth2LinkStartResponse> response =
                controller.startLink("google", userId);

        // then
        verify(linkRequestService)
                .create(userId, OAuthProvider.GOOGLE);

        assertThat(response.getBody().authorizationUrl())
                .isEqualTo("/oauth2/authorization/google");

        String cookie =
                response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie)
                .contains("OAUTH2_LINK_TOKEN=link-token")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/login/oauth2/code");
    }
}
