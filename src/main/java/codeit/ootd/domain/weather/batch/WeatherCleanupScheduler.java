package codeit.ootd.domain.weather.batch;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "ootd.weather.cleanup",
    name = "scheduler-enabled",
    havingValue = "true"
)
public class WeatherCleanupScheduler {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final JobOperator jobOperator;
    private final Job weatherCleanupJob;
    private final Clock clock;

    public WeatherCleanupScheduler(
        JobOperator jobOperator,
        @Qualifier("weatherCleanupJob") Job weatherCleanupJob,
        Clock clock
    ) {
        this.jobOperator = jobOperator;
        this.weatherCleanupJob = weatherCleanupJob;
        this.clock = clock;
    }

    @Scheduled(
        cron = "0 0 3 * * *",
        zone = "Asia/Seoul",
        scheduler = "weatherCleanupTaskScheduler"
    )
    public void run() {

        LocalDate runDate = LocalDate.ofInstant(
            clock.instant(), KOREA_ZONE
        );

        JobParameters parameters = new JobParametersBuilder()
            .addString("runDate", runDate.toString())
            .toJobParameters();

        try {
            var execution = jobOperator.start(
                weatherCleanupJob,
                parameters
            );

            log.info(
                "날씨 데이터 정리 Batch 실행: date={}, status={}",
                runDate,
                execution.getStatus()
            );

        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException e) {

            log.info(
                "날씨 데이터 정리 Batch 중복 실행 생략: date={}",
                runDate
            );

        } catch (Exception e) {
            log.error(
                "날씨 데이터 정리 Batch 실행 실패: date={}",
                runDate,
                e
            );
        }
    }
}
