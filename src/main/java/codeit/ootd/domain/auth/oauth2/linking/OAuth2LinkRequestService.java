package codeit.ootd.domain.auth.oauth2.linking;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class OAuth2LinkRequestService {

    private static final Duration EXPIRATION = Duration.ofMinutes(5);
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final ConcurrentMap<String, OAuth2LinkRequest> requests =
            new ConcurrentHashMap<>();

    private final Clock clock;

    public OAuth2LinkRequestService(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public String create(UUID userId, OAuthProvider provider) {
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        Objects.requireNonNull(provider, "provider는 필수입니다.");

        Instant expiresAt = clock.instant().plus(EXPIRATION);

        OAuth2LinkRequest request =
                new OAuth2LinkRequest(userId, provider, expiresAt);

        String token;

        do {
            byte[] randomBytes = new byte[TOKEN_BYTES];
            secureRandom.nextBytes(randomBytes);

            token = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(randomBytes);

        } while (requests.putIfAbsent(token, request) != null);

        return token;
    }

    public OAuth2LinkRequest consume(String token) {
        if (token == null || token.isBlank()) {
            throw new OtbooException(
                    ErrorCode.OAUTH2_LINK_TOKEN_INVALID
            );
        }

        OAuth2LinkRequest request = requests.remove(token);

        if (request == null) {
            throw new OtbooException(
                    ErrorCode.OAUTH2_LINK_TOKEN_INVALID
            );
        }

        if (!clock.instant().isBefore(request.expiresAt())) {
            throw new OtbooException(
                    ErrorCode.OAUTH2_LINK_TOKEN_EXPIRED
            );
        }

        return request;
    }
}
