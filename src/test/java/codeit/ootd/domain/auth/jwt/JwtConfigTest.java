package codeit.ootd.domain.auth.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(context ->
                            context.getBeanFactory().setConversionService(
                                    ApplicationConversionService.getSharedInstance()
                            )
                    )
                    .withUserConfiguration(JwtConfig.class);

    private final String validSecret = Base64.getEncoder()
            .encodeToString(new byte[32]);

    @Test
    @DisplayName("정상 JWT 설정이면 JwtProvider Bean이 생성된다")
    void jwtProvider_validConfig_success() {
        // given
        // when
        contextRunner
                .withPropertyValues(
                        "jwt.secret=" + validSecret,
                        "jwt.access-token-expiration=30m"
                )
                .run(context -> {
                    // then
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtProvider.class);
                });
    }

    @Test
    @DisplayName("JWT 만료 시간이 0이면 Bean 생성에 실패한다")
    void jwtProvider_zeroExpiration_failed() {
        contextRunner
                .withPropertyValues(
                        "jwt.secret=" + validSecret,
                        "jwt.access-token-expiration=0s"
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .rootCause()
                            .hasMessageContaining("Access Token 만료 시간은 0보다 커야 합니다.");
                });
    }

    @Test
    @DisplayName("JWT 만료 시간이 음수이면 Bean 생성에 실패한다")
    void jwtProvider_negativeExpiration_failed() {
        // when
        contextRunner
                .withPropertyValues(
                        "jwt.secret=" + validSecret,
                        "jwt.access-token-expiration=-30s"
                )
                .run(context -> {
                    // then
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .rootCause()
                            .hasMessageContaining("Access Token 만료 시간은 0보다 커야 합니다.");
                });
    }

    @Test
    @DisplayName("JWT 비밀키가 32바이트보다 짧으면 Bean 생성에 실패한다")
    void jwtProvider_invalidSecret_failed() {
        // given
        String weakSecret = Base64.getEncoder()
                .encodeToString(new byte[16]);

        // when
        contextRunner
                .withPropertyValues(
                        "jwt.secret=" + weakSecret,
                        "jwt.access-token-expiration=30m"
                )
                .run(context -> {
                    // then
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    io.jsonwebtoken.security.WeakKeyException.class
                            );
                });
    }
}

