package codeit.ootd.domain.weather.batch;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.domain.weather.service.WeatherForecastCollector;
import codeit.ootd.domain.weather.service.WeatherService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCollectionTasklet implements Tasklet {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(2);

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(10);

    private final WeatherLocationRepository weatherLocationRepository;
    private final WeatherForecastCollector weatherForecastCollector;
    private final WeatherService weatherService;

    @Override
    public RepeatStatus execute(
        @NonNull StepContribution contribution,
        @NonNull ChunkContext chunkContext
    ) {

        List<WeatherLocation> locations =
            weatherLocationRepository
                .findTop20ByNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                    Instant.now()
                );

        int failureCount = 0;

        for (WeatherLocation location : locations) {
            try {
                collectWithRetry(location);

                LocalDate today = LocalDate.now(KOREA_ZONE);

                for (int day = 0; day < 5; day++) {
                    weatherService.calculateAndSaveDailyWeather(
                        location, today.plusDays(day)
                    );
                }

                Instant completedAt = Instant.now();

                location.completeCollection(
                    completedAt,
                    completedAt.plus(REFRESH_INTERVAL)
                );

                weatherLocationRepository.save(location);

                log.info("날씨 수집 완료: cellId={}", location.getCellId());

            } catch (Exception e) {
                failureCount++;

                log.error("날씨 수집 실패: cellId={}", location.getCellId(), e);

                Instant failedAt = Instant.now();

                try {
                    location.scheduleRetry(failedAt, failedAt.plus(RETRY_INTERVAL));

                    weatherLocationRepository.save(location);

                } catch (Exception retryException) {
                    log.error(
                        "날씨 수집 재시도 예약 실패: cellId={}",
                        location.getCellId(),
                        retryException
                    );
                }
            }
        }

        log.info("날씨 Batch 종료: 대상 {}개, 실패 {}개", locations.size(), failureCount);

        if (failureCount > 0) {
            throw new IllegalStateException(
                "날씨 수집에 실패한 셀이 있습니다: " + failureCount
            );
        }

        return RepeatStatus.FINISHED;
    }

    private void collectWithRetry(WeatherLocation location) {

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                weatherForecastCollector.collectForecasts(location);
                return;

            } catch (Exception e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException("날씨 예보 수집 재시도 횟수 초과", e);
                }

                log.warn(
                    "날씨 예보 수집 재시도: cellId={}, 다음 시도={}/{}",
                    location.getCellId(),
                    attempt + 1,
                    MAX_ATTEMPTS
                );

                try {
                    Thread.sleep(1_000L * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                        "날씨 예보 재시도가 중단되었습니다.",
                        interruptedException
                    );
                }
            }
        }
    }
}
