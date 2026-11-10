package codeit.ootd.domain.weather.batch;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.domain.weather.service.WeatherForecastCollector;
import codeit.ootd.domain.weather.service.WeatherService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCollectionTasklet implements Tasklet {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final int BATCH_SIZE = 20;
    private static final int MAX_LOCATIONS_PER_JOB = 100;
    private static final Duration MAX_JOB_DURATION = Duration.ofMinutes(5);

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(2);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(10);

    private final WeatherLocationRepository weatherLocationRepository;
    private final WeatherForecastCollector weatherForecastCollector;
    private final WeatherService weatherService;
    private final Clock clock;
    private final WeatherBatchRateLimiter weatherBatchRateLimiter;

    @Override
    public RepeatStatus execute(
        @NonNull StepContribution contribution,
        @NonNull ChunkContext chunkContext
    ) {

        Instant startedAt = clock.instant();
        Instant deadline = startedAt.plus(MAX_JOB_DURATION);

        int processedCount = 0;
        int failureCount = 0;

        Set<UUID> processedLocationIds = new HashSet<>();

        while (processedCount < MAX_LOCATIONS_PER_JOB
            && clock.instant().isBefore(deadline)) {

            List<WeatherLocation> locations =
                weatherLocationRepository
                    .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                        clock.instant()
                    );

            if (locations.isEmpty()) {
                break;
            }

            for (WeatherLocation location : locations) {

                if (processedCount >= MAX_LOCATIONS_PER_JOB
                    || !clock.instant().isBefore(deadline)) {
                    break;
                }

                UUID locationId = location.getId();

                if (locationId != null && !processedLocationIds.add(locationId)) {
                    throw new IllegalStateException(
                        "동일 Job에서 이미 처리한 지역이 다시 조회되었습니다: "
                            + location.getCellId()
                    );
                }

                processedCount++;

                try {
                    collectWithRetry(location);

                    LocalDate today = LocalDate.ofInstant(
                        clock.instant(),
                        KOREA_ZONE
                    );

                    for (int day = 0; day < 5; day++) {
                        weatherService.calculateAndSaveDailyWeather(
                            location,
                            today.plusDays(day)
                        );
                    }

                    Instant completedAt = clock.instant();

                    location.completeCollection(
                        completedAt,
                        completedAt.plus(REFRESH_INTERVAL)
                    );

                    weatherLocationRepository.save(location);

                    log.info(
                        "날씨 수집 완료: cellId={}",
                        location.getCellId()
                    );

                } catch (Exception e) {
                    failureCount++;

                    log.error(
                        "날씨 수집 실패: cellId={}",
                        location.getCellId(),
                        e
                    );

                    Instant failedAt = clock.instant();

                    try {
                        location.scheduleRetry(
                            failedAt,
                            failedAt.plus(RETRY_INTERVAL)
                        );

                        weatherLocationRepository.save(location);

                    } catch (Exception retryException) {
                        log.error(
                            "날씨 수집 재시도 예약 실패: cellId={}",
                            location.getCellId(),
                            retryException
                        );

                        throw new IllegalStateException(
                            "날씨 수집 재시도 예약 저장 실패: "
                                + location.getCellId(),
                            retryException
                        );
                    }

                    if (e instanceof HttpClientErrorException clientError) {
                        int status = clientError.getStatusCode().value();

                        if (status == 401 || status == 403 || status == 429) {
                            log.error(
                                "OWM 호출 중단: HTTP {}, cellId={}",
                                status,
                                location.getCellId()
                            );

                            throw new IllegalStateException(
                                "OWM HTTP " + status + " 오류로 Batch를 중단합니다.",
                                e
                            );
                        }
                    }
                }
            }

            if (locations.size() < BATCH_SIZE) {
                break;
            }
        }

        log.info(
            "날씨 Batch 종료: 처리 {}개, 실패 {}개, 실행 시간 {}ms",
            processedCount,
            failureCount,
            Duration.between(startedAt, clock.instant()).toMillis()
        );

        if (failureCount > 0) {
            throw new IllegalStateException(
                "날씨 수집에 실패한 셀이 있습니다: " + failureCount
            );
        }

        return RepeatStatus.FINISHED;
    }

    private void collectWithRetry(WeatherLocation location) {

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

            weatherBatchRateLimiter.acquire();

            try {
                weatherForecastCollector.collectForecasts(location);
                return;

            } catch (HttpServerErrorException | ResourceAccessException e) {

                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException(
                        "날씨 예보 수집 재시도 횟수 초과",
                        e
                    );
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
