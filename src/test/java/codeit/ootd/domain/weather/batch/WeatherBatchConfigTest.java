package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class WeatherBatchConfigTest {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("weatherCollectionJob")
    private Job weatherCollectionJob;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private WeatherLocationRepository weatherLocationRepository;

    @Test
    @DisplayName("날씨 Batch 실행 완료 시 Job 실행 메트릭을 기록한다")
    void weatherCollectionJobCompletes() throws Exception {
        JobExecution execution = jobOperator.start(
            weatherCollectionJob,
            new JobParametersBuilder()
                .addString("requestId", UUID.randomUUID().toString())
                .toJobParameters()
        );

        assertThat(execution.getStatus())
            .isEqualTo(BatchStatus.COMPLETED);

        Timer jobTimer = meterRegistry.get("spring.batch.job")
            .tag("spring.batch.job.name", "weatherCollectionJob")
            .tag("spring.batch.job.status", "COMPLETED")
            .timer();

        assertThat(jobTimer.count()).isGreaterThanOrEqualTo(1);
        assertThat(jobTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("날씨 Batch 실패 시 실패 상태와 실행 시간을 메트릭에 기록한다")
    void weatherCollectionJobRecordsFailureMetrics() throws Exception {
        when(weatherLocationRepository
            .findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                any(Instant.class)
            ))
            .thenThrow(new IllegalStateException("테스트용 조회 실패"));

        JobExecution execution = jobOperator.start(
            weatherCollectionJob,
            new JobParametersBuilder()
                .addString("requestId", UUID.randomUUID().toString())
                .toJobParameters()
        );

        assertThat(execution.getStatus())
            .isEqualTo(BatchStatus.FAILED);

        var jobTimer = meterRegistry.get("spring.batch.job")
            .tag("spring.batch.job.name", "weatherCollectionJob")
            .tag("spring.batch.job.status", "FAILED")
            .timer();

        assertThat(jobTimer.count()).isGreaterThanOrEqualTo(1);
        assertThat(jobTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isPositive();
    }
}
