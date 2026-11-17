package codeit.ootd.domain.weather.batch;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WeatherBatchRateLimiter {

    private final long intervalNanos;
    private long nextAllowedNanos = Long.MIN_VALUE;

    public WeatherBatchRateLimiter(
        @Value("${ootd.weather.batch.min-request-interval:2s}")Duration interval
    ) {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException(
                "Batch 호출 간격은 0보다 커야 합니다."
            );
        }

        this.intervalNanos = interval.toNanos();
    }

    public synchronized void acquire() {

        long now = System.nanoTime();

        if (nextAllowedNanos != Long.MIN_VALUE) {
            long waitNanos = nextAllowedNanos - now;

            if (waitNanos > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(waitNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                        "Batch API 호출 대기가 중단되었습니다.",
                        e
                    );
                }
            }
        }

        nextAllowedNanos = System.nanoTime() + intervalNanos;
    }
}
