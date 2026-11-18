package codeit.ootd.domain.auth.controller;

import codeit.ootd.domain.auth.docs.AuthApiDocs;
import codeit.ootd.domain.auth.dto.request.ResetPasswordRequest;
import codeit.ootd.domain.auth.dto.request.SignInRequest;
import codeit.ootd.domain.auth.dto.response.SignInResponse;
import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.dto.response.TokenRefreshResponse;
import codeit.ootd.domain.auth.dto.response.TokenRefreshResult;
import codeit.ootd.domain.auth.oauth2.RefreshTokenCookieFactory;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.auth.service.PasswordResetRateLimiter;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController implements AuthApiDocs {

    private final AuthService authService;
    private final PasswordResetRateLimiter passwordResetRateLimiter;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @Value("${auth.cookie.secure:true}")
    private boolean cookieSecure;

    @Override
    @PostMapping(
            value = "/sign-in",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<SignInResponse> signIn(
            @Valid @ModelAttribute SignInRequest request
    ) {
        SignInResult result = authService.signIn(
                request.username(),
                request.password()
        );


        ResponseCookie refreshTokenCookie =
                refreshTokenCookieFactory.create(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(SignInResponse.from(result));
    }

    @Override
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        passwordResetRateLimiter.check(
                request.email(),
                httpRequest.getRemoteAddr()
        );

        authService.resetPassword(request.email());

        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/csrf-token")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @CookieValue(value = "REFRESH_TOKEN", required = false)
            String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        TokenRefreshResult result = authService.refresh(refreshToken);

        ResponseCookie cookie =
                refreshTokenCookieFactory.create(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(TokenRefreshResponse.from(result));
    }

    @Override
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(
            @Parameter(hidden = true)
            @CookieValue(value = "REFRESH_TOKEN", required = false)
            String refreshToken
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.signOut(refreshToken);
        }

        ResponseCookie cookie = refreshTokenCookieFactory.delete();

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
        return ResponseCookie.from("REFRESH_TOKEN", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
