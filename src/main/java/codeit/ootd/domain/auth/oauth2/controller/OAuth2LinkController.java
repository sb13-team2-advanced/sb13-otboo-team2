package codeit.ootd.domain.auth.oauth2.controller;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import java.time.Duration;
import java.util.UUID;

import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkRequestService;
import codeit.ootd.domain.auth.oauth2.linking.OAuth2LinkStartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/oauth2/link")
public class OAuth2LinkController {

    private final OAuth2LinkRequestService linkRequestService;
    private final boolean cookieSecure;

    public OAuth2LinkController(
            OAuth2LinkRequestService linkRequestService,
            @Value("${auth.cookie.secure:true}") boolean cookieSecure
    ) {
        this.linkRequestService = linkRequestService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/{provider:google|kakao}")
    public ResponseEntity<OAuth2LinkStartResponse> startLink(
            @PathVariable String provider,
            @AuthenticationPrincipal UUID userId
    ) {
        OAuthProvider oauthProvider = switch (provider) {
            case "google" -> OAuthProvider.GOOGLE;
            case "kakao" -> OAuthProvider.KAKAO;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 소셜 로그인 제공자입니다."
            );
        };

        String token = linkRequestService.create(
                userId,
                oauthProvider
        );

        ResponseCookie cookie = ResponseCookie
                .from("OAUTH2_LINK_TOKEN", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/login/oauth2/code")
                .maxAge(Duration.ofMinutes(5))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new OAuth2LinkStartResponse(
                        "/oauth2/authorization/" + provider
                ));
    }
}
