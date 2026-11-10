package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.domain.weather.service.WeatherForecastCollector;
import codeit.ootd.domain.weather.service.WeatherService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("날씨 수집 Batch Tasklet 테스트")
class WeatherCollectionTaskletTest {

    @Mock
    private WeatherLocationRepository weatherLocationRepository;

    @Mock
    private WeatherForecastCollector weatherForecastCollector;

    @Mock
    private WeatherService weatherService;

    @Mock
    private WeatherBatchRateLimiter weatherBatchRateLimiter;

    @Mock
    private WeatherLocation location;

    @Mock
    private Clock clock;

    private final SimpleMeterRegistry meterRegistry =
        new SimpleMeterRegistry();

    private WeatherCollectionTasklet tasklet;

    private final AtomicReference<Instant> currentTime =
        new AtomicReference<>(
            Instant.parse("2026-10-30T00:00:00Z")
        );

    @BeforeEach
    void setUp() {
        tasklet = new WeatherCollectionTasklet(
            weatherLocationRepository,
            weatherForecastCollector,
            weatherService,
            clock,
            weatherBatchRateLimiter,
            meterRegistry
        );

        when(clock.instant()).thenAnswer(
            invocation -> currentTime.get()
        );
    }

    @Test
    @DisplayName("최초 수집을 완료한 대상 셀의 예보를 수집하고 5일치 날씨를 집계한다")
    void executeCollectsAndAggregatesWeather() {

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location));

        RepeatStatus result = tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        );

        verify(weatherLocationRepository)
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            );

        verify(weatherBatchRateLimiter).acquire();

        verify(weatherForecastCollector)
            .collectForecasts(location);

        verify(weatherService, times(5))
            .calculateAndSaveDailyWeather(
                eq(location),
                any(LocalDate.class)
            );

        verify(location).completeCollection(
            any(Instant.class),
            any(Instant.class)
        );

        verify(weatherLocationRepository).save(location);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("일부 셀의 수집이 3회 실패해도 재시도를 예약하고 나머지 셀을 처리한다")
    void executeContinuesWhenCollectionFails() {

        WeatherLocation secondLocation = mock(WeatherLocation.class);

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location, secondLocation));

        doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .when(weatherForecastCollector)
            .collectForecasts(location);

        assertThatThrownBy(() -> tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("1");

        verify(weatherBatchRateLimiter, times(4))
            .acquire();

        verify(weatherForecastCollector, times(3))
            .collectForecasts(location);

        verify(location).scheduleRetry(
            any(Instant.class),
            any(Instant.class)
        );

        verify(location, never()).completeCollection(
            any(Instant.class),
            any(Instant.class)
        );

        verify(weatherLocationRepository).save(location);

        verify(weatherForecastCollector)
            .collectForecasts(secondLocation);

        verify(weatherService, times(5))
            .calculateAndSaveDailyWeather(
                eq(secondLocation),
                any(LocalDate.class)
            );

        verify(weatherLocationRepository).save(secondLocation);
    }

    @Test
    @DisplayName("예보 수집이 두 번 실패해도 세 번째 시도에 성공하면 정상 완료한다")
    void executeSucceedsAfterRetries() {

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location));

        doThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
            .doThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
            .doNothing()
            .when(weatherForecastCollector)
            .collectForecasts(location);

        RepeatStatus result = tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        );

        verify(weatherBatchRateLimiter, times(3))
            .acquire();

        verify(weatherForecastCollector, times(3))
            .collectForecasts(location);

        verify(weatherService, times(5))
            .calculateAndSaveDailyWeather(
                eq(location),
                any(LocalDate.class)
            );

        verify(location).completeCollection(
            any(Instant.class),
            any(Instant.class)
        );

        verify(location, never()).scheduleRetry(
            any(Instant.class),
            any(Instant.class)
        );

        verify(weatherLocationRepository).save(location);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("Batch 수집 대상이 없으면 예보 수집과 일별 집계를 실행하지 않는다")
    void executeSkipsCollectionWhenNoLocationsAreDue() {

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of());

        RepeatStatus result = tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        );

        verify(weatherLocationRepository)
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            );

        verifyNoInteractions(weatherBatchRateLimiter);
        verifyNoInteractions(weatherForecastCollector);
        verifyNoInteractions(weatherService);

        verify(weatherLocationRepository, never())
            .save(any(WeatherLocation.class));

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("수집 대상이 20개를 초과하면 다음 묶음을 연속 처리한다")
    void executeProcessesMoreThanTwentyLocations() {

        List<WeatherLocation> firstTwenty = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            WeatherLocation item = mock(WeatherLocation.class);
            when(item.getId()).thenReturn(UUID.randomUUID());
            firstTwenty.add(item);
        }

        WeatherLocation lastLocation = mock(WeatherLocation.class);
        when(lastLocation.getId()).thenReturn(UUID.randomUUID());

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(firstTwenty)
            .thenReturn(List.of(lastLocation));

        RepeatStatus result = tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        );

        verify(weatherLocationRepository, times(2))
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            );

        verify(weatherBatchRateLimiter, times(21))
            .acquire();

        verify(weatherForecastCollector, times(21))
            .collectForecasts(any(WeatherLocation.class));

        verify(weatherService, times(105))
            .calculateAndSaveDailyWeather(
                any(WeatherLocation.class),
                any(LocalDate.class)
            );

        verify(weatherLocationRepository, times(21))
            .save(any(WeatherLocation.class));

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("수집 대상이 100개에 도달하면 추가 조회 없이 종료한다")
    void executeStopsAtOneHundredLocations() {

        List<List<WeatherLocation>> pages = new ArrayList<>();

        for (int page = 0; page < 5; page++) {
            List<WeatherLocation> locations = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                WeatherLocation item = mock(WeatherLocation.class);
                when(item.getId()).thenReturn(UUID.randomUUID());
                locations.add(item);
            }

            pages.add(locations);
        }

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(pages.get(0))
            .thenReturn(pages.get(1))
            .thenReturn(pages.get(2))
            .thenReturn(pages.get(3))
            .thenReturn(pages.get(4));

        RepeatStatus result = tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        );

        verify(weatherLocationRepository, times(5))
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            );

        verify(weatherBatchRateLimiter, times(100))
            .acquire();

        verify(weatherForecastCollector, times(100))
            .collectForecasts(any(WeatherLocation.class));

        verify(weatherService, times(500))
            .calculateAndSaveDailyWeather(
                any(WeatherLocation.class),
                any(LocalDate.class)
            );

        verify(weatherLocationRepository, times(100))
            .save(any(WeatherLocation.class));

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("실행 시간이 5분에 도달하면 다음 지역의 수집을 시작하지 않는다")
    void executeStopsWhenJobDurationIsExceeded() {

        WeatherLocation secondLocation = mock(WeatherLocation.class);

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location, secondLocation));

        doAnswer(invocation -> {
            currentTime.updateAndGet(
                time -> time.plus(Duration.ofMinutes(5))
            );
            return null;
        })
            .when(weatherForecastCollector)
            .collectForecasts(location);

        RepeatStatus result = tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        );

        verify(weatherBatchRateLimiter, times(1))
            .acquire();

        verify(weatherForecastCollector)
            .collectForecasts(location);

        verify(weatherService, times(5))
            .calculateAndSaveDailyWeather(
                eq(location),
                any(LocalDate.class)
            );

        verify(weatherForecastCollector, never())
            .collectForecasts(secondLocation);

        verify(weatherLocationRepository, times(1))
            .save(any(WeatherLocation.class));

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("OWM 429 오류가 발생하면 재시도하지 않고 Batch를 중단한다")
    void executeStopsBatchOnRateLimitError() {

        WeatherLocation secondLocation = mock(WeatherLocation.class);

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location, secondLocation));

        doThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))
            .when(weatherForecastCollector)
            .collectForecasts(location);

        assertThatThrownBy(() -> tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("429");

        verify(weatherBatchRateLimiter, times(1)).acquire();

        verify(weatherForecastCollector, times(1))
            .collectForecasts(location);

        verify(location).scheduleRetry(
            any(Instant.class),
            any(Instant.class)
        );

        verify(weatherForecastCollector, never())
            .collectForecasts(secondLocation);
    }

    @Test
    @DisplayName("OWM 인증 오류가 발생하면 재시도하지 않고 Batch를 중단한다")
    void executeStopsBatchOnUnauthorizedError() {

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location));

        doThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
            .when(weatherForecastCollector)
            .collectForecasts(location);

        assertThatThrownBy(() -> tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("401");

        verify(weatherBatchRateLimiter, times(1)).acquire();

        verify(weatherForecastCollector, times(1))
            .collectForecasts(location);

        verify(location).scheduleRetry(
            any(Instant.class),
            any(Instant.class)
        );
    }


    @Test
    @DisplayName("OWM 403 오류가 발생하면 재시도 없이 Batch를 중단한다")
    void executeStopsBatchOnForbiddenError() {

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location));

        doThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN))
            .when(weatherForecastCollector)
            .collectForecasts(location);

        assertThatThrownBy(() -> tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("403");

        verify(weatherBatchRateLimiter, times(1)).acquire();

        verify(weatherForecastCollector, times(1))
            .collectForecasts(location);

        verify(location).scheduleRetry(
            any(Instant.class),
            any(Instant.class)
        );
    }

    @Test
    @DisplayName("OWM 타임아웃 발생 후 재시도에 성공하면 정상 완료한다")
    void executeRetriesOnTimeout() {

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location));

        doThrow(new ResourceAccessException("Read timed out"))
            .doNothing()
            .when(weatherForecastCollector)
            .collectForecasts(location);

        RepeatStatus result = tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        );

        verify(weatherBatchRateLimiter, times(2)).acquire();

        verify(weatherForecastCollector, times(2))
            .collectForecasts(location);

        verify(location).completeCollection(
            any(Instant.class),
            any(Instant.class)
        );

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("예상하지 못한 오류가 발생하면 재시도하지 않는다")
    void executeDoesNotRetryUnexpectedFailure() {

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location));

        doThrow(new IllegalStateException("DB 저장 실패"))
            .when(weatherForecastCollector)
            .collectForecasts(location);

        assertThatThrownBy(() -> tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("1");

        verify(weatherBatchRateLimiter, times(1)).acquire();

        verify(weatherForecastCollector, times(1))
            .collectForecasts(location);

        verify(location).scheduleRetry(
            any(Instant.class),
            any(Instant.class)
        );
    }

    @Test
    @DisplayName("셀 수집 성공과 실패 횟수를 메트릭에 기록한다")
    void executeRecordsCollectionMetrics() {
        WeatherLocation failedLocation = mock(WeatherLocation.class);

        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenReturn(List.of(location, failedLocation));

        doNothing()
            .when(weatherForecastCollector)
            .collectForecasts(location);

        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
            .when(weatherForecastCollector)
            .collectForecasts(failedLocation);

        assertThatThrownBy(() -> tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        ))
            .isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry.get("ootd.weather.batch.cells")
            .tag("result", "success")
            .counter()
            .count())
            .isEqualTo(1.0);

        assertThat(meterRegistry.get("ootd.weather.batch.cells")
            .tag("result", "failure")
            .counter()
            .count())
            .isEqualTo(1.0);
    }
}
