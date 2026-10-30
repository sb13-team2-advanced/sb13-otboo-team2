package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

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
    private WeatherLocation location;

    @InjectMocks
    private WeatherCollectionTasklet tasklet;

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

        verify(weatherForecastCollector).collectForecasts(location);

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

        doThrow(new RuntimeException("OWM API 오류"))
            .when(weatherForecastCollector)
            .collectForecasts(location);

        assertThatThrownBy(() -> tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("1");

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

        doThrow(new RuntimeException("일시적 API 오류"))
            .doThrow(new RuntimeException("일시적 API 오류"))
            .doNothing()
            .when(weatherForecastCollector)
            .collectForecasts(location);

        RepeatStatus result = tasklet.execute(
            mock(StepContribution.class),
            mock(ChunkContext.class)
        );

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

        verifyNoInteractions(weatherForecastCollector);
        verifyNoInteractions(weatherService);

        verify(weatherLocationRepository, never())
            .save(any(WeatherLocation.class));

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }
}
