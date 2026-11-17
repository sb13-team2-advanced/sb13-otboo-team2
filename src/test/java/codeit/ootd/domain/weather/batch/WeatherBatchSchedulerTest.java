package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

class WeatherBatchSchedulerTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                WeatherBatchScheduler.class,
                WeatherSchedulerConfig.class
            )
            .withBean(JobOperator.class, () -> mock(JobOperator.class))
            .withBean("weatherCollectionJob", Job.class, () -> mock(Job.class));

    @Test
    @DisplayName("스케줄러가 비활성화되면 Bean을 등록하지 않는다")
    void schedulerDisabled() {
        contextRunner
            .withPropertyValues("ootd.weather.batch.scheduler-enabled=false")
            .run(context -> {
                assertThat(context)
                    .doesNotHaveBean(WeatherBatchScheduler.class);
            });
    }

    @Test
    @DisplayName("스케줄러가 활성화되면 Bean을 등록한다")
    void schedulerEnabled() {
        contextRunner
            .withPropertyValues("ootd.weather.batch.scheduler-enabled=true")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(WeatherBatchScheduler.class);
            });
    }

    @Test
    @DisplayName("스케줄러 실행 시 날씨 수집 Job을 시작한다")
    void schedulerStartsWeatherJob() throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        Job job = mock(Job.class);
        JobExecution execution = mock(JobExecution.class);

        when(jobOperator.start(eq(job), any(JobParameters.class)))
            .thenReturn(execution);

        WeatherBatchScheduler scheduler =
            new WeatherBatchScheduler(jobOperator, job);

        scheduler.run();

        verify(jobOperator).start(
            eq(job),
            any(JobParameters.class)
        );
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("이미 실행 중인 날씨 Batch는 중복 실행으로 처리한다")
    void schedulerSkipsAlreadyRunningJob(CapturedOutput output) throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        Job job = mock(Job.class);

        when(jobOperator.start(eq(job), any(JobParameters.class)))
            .thenThrow(new JobExecutionAlreadyRunningException("이미 실행 중"));

        WeatherBatchScheduler scheduler =
            new WeatherBatchScheduler(jobOperator, job);

        scheduler.run();

        verify(jobOperator).start(
            eq(job),
            any(JobParameters.class)
        );

        assertThat(output.getAll())
            .contains("날씨 Batch 중복 실행 생략")
            .doesNotContain("날씨 Batch 실행 실패");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("이미 완료된 날씨 Batch는 중복 실행으로 처리한다")
    void schedulerSkipsAlreadyCompletedJob(CapturedOutput output) throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        Job job = mock(Job.class);

        when(jobOperator.start(eq(job), any(JobParameters.class)))
            .thenThrow(new JobInstanceAlreadyCompleteException("이미 완료됨"));

        WeatherBatchScheduler scheduler =
            new WeatherBatchScheduler(jobOperator, job);

        scheduler.run();

        verify(jobOperator).start(
            eq(job),
            any(JobParameters.class)
        );

        assertThat(output.getAll())
            .contains("날씨 Batch 중복 실행 생략")
            .doesNotContain("날씨 Batch 실행 실패");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("예상하지 못한 날씨 Batch 실행 오류는 오류 로그로 기록한다")
    void schedulerLogsUnexpectedFailure(CapturedOutput output) throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        Job job = mock(Job.class);

        when(jobOperator.start(eq(job), any(JobParameters.class)))
            .thenThrow(new IllegalStateException("예상하지 못한 오류"));

        WeatherBatchScheduler scheduler =
            new WeatherBatchScheduler(jobOperator, job);

        scheduler.run();

        verify(jobOperator).start(
            eq(job),
            any(JobParameters.class)
        );

        assertThat(output.getAll())
            .contains("날씨 Batch 실행 실패")
            .contains("예상하지 못한 오류")
            .doesNotContain("날씨 Batch 중복 실행 생략");
    }
}
