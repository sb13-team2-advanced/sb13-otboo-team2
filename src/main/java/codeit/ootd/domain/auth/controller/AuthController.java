package codeit.ootd.domain.auth.controller;


import codeit.ootd.domain.auth.dto.request.SignInRequest;
import codeit.ootd.domain.auth.dto.response.SignInResponse;
import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.dto.response.TokenRefreshResponse;
import codeit.ootd.domain.auth.dto.response.TokenRefreshResult;
import codeit.ootd.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${auth.cookie.secure:true}")
    private boolean cookieSecure;

    @PostMapping(
            value = "/sign-in",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<SignInResponse> signIn(@Valid @ModelAttribute SignInRequest request) {

        SignInResult result = authService.signIn(
                request.username(),
                request.password()
        );

        ResponseCookie refreshTokenCookie = createRefreshTokenCookie(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(SignInResponse.from(result));
    }

    @GetMapping({"/csrf-token", "/csrf"})
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @CookieValue("refreshToken") String refreshToken
    ) {
        TokenRefreshResult result = authService.refresh(refreshToken);

        ResponseCookie cookie = createRefreshTokenCookie(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new TokenRefreshResponse(result.accessToken()));
    }

    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(
            @CookieValue(value = "refreshToken", required = false)
            String refreshToken
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.signOut(refreshToken);
        }

        ResponseCookie cookie = deleteRefreshTokenCookie();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }


    private ResponseCookie createRefreshTokenCookie(String token) {
        return buildRefreshTokenCookie(token, Duration.ofDays(7));
    }

    private ResponseCookie deleteRefreshTokenCookie() {
        return buildRefreshTokenCookie("", Duration.ZERO);
    }

    private ResponseCookie buildRefreshTokenCookie(
            String token,
            Duration maxAge
    ) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }



}
