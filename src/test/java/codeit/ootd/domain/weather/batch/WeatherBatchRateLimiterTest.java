package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("날씨 Batch 호출 속도 제한 테스트")
class WeatherBatchRateLimiterTest {

    @Test
    @DisplayName("연속 호출 사이에 설정된 최소 간격을 유지한다")
    void acquireRespectsMinimumInterval() {

        WeatherBatchRateLimiter rateLimiter =
            new WeatherBatchRateLimiter(Duration.ofMillis(50));

        long startedAt = System.nanoTime();

        rateLimiter.acquire();
        rateLimiter.acquire();
        rateLimiter.acquire();

        long elapsedNanos = System.nanoTime() - startedAt;

        assertThat(elapsedNanos)
            .isGreaterThanOrEqualTo(
                Duration.ofMillis(95).toNanos()
            );
    }

    @Test
    @DisplayName("호출 간격이 0이면 예외가 발생한다")
    void rejectZeroInterval() {

        assertThatThrownBy(() ->
            new WeatherBatchRateLimiter(Duration.ZERO)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("호출 간격이 음수이면 예외가 발생한다")
    void rejectNegativeInterval() {

        assertThatThrownBy(() ->
            new WeatherBatchRateLimiter(Duration.ofSeconds(-1))
        )
            .isInstanceOf(IllegalArgumentException.class);
    }
}
