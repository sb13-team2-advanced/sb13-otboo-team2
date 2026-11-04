package codeit.ootd.domain.weather.batch;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
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
    prefix = "ootd.weather.batch",
    name = "scheduler-enabled",
    havingValue = "true"
)
public class WeatherBatchScheduler {

    private final JobOperator jobOperator;
    private final Job weatherCollectionJob;

    public WeatherBatchScheduler(
        JobOperator jobOperator,
        @Qualifier("weatherCollectionJob") Job weatherCollectionJob
    ) {
        this.jobOperator = jobOperator;
        this.weatherCollectionJob = weatherCollectionJob;
    }

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void run() {

        long runSlot = Instant.now().getEpochSecond() / 600;

        JobParameters parameters = new JobParametersBuilder()
            .addLong("runSlot", runSlot)
            .toJobParameters();

        try {
            var execution = jobOperator.start(
                weatherCollectionJob,
                parameters
            );

            log.info(
                "날씨 Batch 실행: slot={}, status={}",
                runSlot,
                execution.getStatus()
            );

        } catch (Exception e) {
            log.error("날씨 Batch 실행 실패: slot={}", runSlot, e);
        }
    }
}
