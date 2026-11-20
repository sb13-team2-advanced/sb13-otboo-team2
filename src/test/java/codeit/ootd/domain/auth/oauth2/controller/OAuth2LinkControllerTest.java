package codeit.ootd.domain.auth.oauth2.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.entity.OAuthProvider;

import java.util.Map;
import java.util.UUID;

import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequestService;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkStartResponse;
import codeit.ootd.domain.auth.service.SocialAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OAuth2LinkControllerTest {

    private OAuth2LinkRequestService linkRequestService;
    private OAuth2LinkController controller;
    private SocialAccountService socialAccountService;

    @BeforeEach
    void setUp() {
        linkRequestService = mock(OAuth2LinkRequestService.class);
        socialAccountService = mock(SocialAccountService.class);
        controller = new OAuth2LinkController(
                linkRequestService,
                socialAccountService,
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
                .contains("Path=/oauth2/authorization");
    }

    @Test
    @DisplayName("인증된 사용자의 Google·Kakao 연동 상태를 반환한다")
    void getLinkedProviders_returnsProviderStatus() {
        // given
        UUID userId = UUID.randomUUID();

        Map<String, Boolean> linkedProviders = Map.of(
                "google", true,
                "kakao", false
        );

        when(socialAccountService.getLinkedProviders(userId))
                .thenReturn(linkedProviders);

        // when
        ResponseEntity<Map<String, Boolean>> response =
                controller.getLinkedProviders(userId);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .containsEntry("google", true)
                .containsEntry("kakao", false)
                .hasSize(2);

        verify(socialAccountService)
                .getLinkedProviders(userId);
    }

}
