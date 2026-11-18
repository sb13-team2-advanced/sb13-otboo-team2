package codeit.ootd.domain.auth.oauth2.linking;

import static org.assertj.core.api.Assertions.*;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuth2LinkRequestServiceTest {

    private OAuth2LinkRequestService service;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-11-18T00:00:00Z"),
            ZoneOffset.UTC
    );

    @BeforeEach
    void setUp() {
        service = new OAuth2LinkRequestService(clock);
    }

    @Test
    @DisplayName("연동 요청 생성 후 올바른 사용자와 제공자를 조회한다")
    void createAndConsume_success() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        String token = service.create(
                userId,
                OAuthProvider.GOOGLE
        );

        OAuth2LinkRequest result = service.consume(token);

        // then
        assertThat(token).isNotBlank();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.provider())
                .isEqualTo(OAuthProvider.GOOGLE);
        assertThat(result.expiresAt())
                .isAfter(clock.instant());
    }

    @Test
    @DisplayName("이미 사용한 연동 요청은 다시 사용할 수 없다")
    void consume_reusedToken_throwsException() {
        UUID userId = UUID.randomUUID();

        String token = service.create(
                userId,
                OAuthProvider.KAKAO
        );

        service.consume(token);

        assertThatThrownBy(() -> service.consume(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("존재하지 않는 연동 요청은 거부한다")
    void consume_unknownToken_throwsException() {
        assertThatThrownBy(
                () -> service.consume("unknown-token")
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
