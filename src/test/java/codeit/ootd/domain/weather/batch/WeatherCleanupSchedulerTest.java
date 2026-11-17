package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WeatherCleanupSchedulerTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                WeatherCleanupScheduler.class,
                WeatherSchedulerConfig.class
            )
            .withBean(JobOperator.class, () -> mock(JobOperator.class))
            .withBean("weatherCleanupJob", Job.class, () -> mock(Job.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    @DisplayName("정리 스케줄러가 비활성화되면 Bean을 등록하지 않는다")
    void schedulerDisabled() {
        contextRunner
            .withPropertyValues("ootd.weather.cleanup.scheduler-enabled=false")
            .run(context ->
                assertThat(context)
                    .doesNotHaveBean(WeatherCleanupScheduler.class)
            );
    }

    @Test
    @DisplayName("정리 스케줄러가 활성화되면 Bean을 등록한다")
    void schedulerEnabled() {
        contextRunner
            .withPropertyValues("ootd.weather.cleanup.scheduler-enabled=true")
            .run(context ->
                assertThat(context)
                    .hasSingleBean(WeatherCleanupScheduler.class)
            );
    }

    @Test
    @DisplayName("정리 스케줄러가 한국 날짜를 기준으로 Job을 실행한다")
    void schedulerStartsJobWithKoreanDate() throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        Job job = mock(Job.class);
        JobExecution execution = mock(JobExecution.class);

        Clock clock = Clock.fixed(
            Instant.parse("2026-11-16T15:30:00Z"),
            ZoneOffset.UTC
        );

        when(jobOperator.start(eq(job), any(JobParameters.class)))
            .thenReturn(execution);

        WeatherCleanupScheduler scheduler =
            new WeatherCleanupScheduler(jobOperator, job, clock);

        scheduler.run();

        ArgumentCaptor<JobParameters> captor =
            ArgumentCaptor.forClass(JobParameters.class);

        verify(jobOperator).start(eq(job), captor.capture());

        assertThat(captor.getValue().getString("runDate"))
            .isEqualTo("2026-11-17");
    }
}
