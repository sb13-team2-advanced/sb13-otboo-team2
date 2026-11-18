package codeit.ootd.domain.auth.oauth2;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {

    private final boolean secure;

    public RefreshTokenCookieFactory(
            @Value("${auth.cookie.secure:true}") boolean secure
    ) {
        this.secure = secure;
    }

    public ResponseCookie create(String token) {
        return build(token, Duration.ofDays(7));
    }

    public ResponseCookie delete() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String token, Duration maxAge) {
        return ResponseCookie.from("REFRESH_TOKEN", token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
