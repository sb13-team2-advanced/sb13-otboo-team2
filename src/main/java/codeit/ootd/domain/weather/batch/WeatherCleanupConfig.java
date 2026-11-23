package codeit.ootd.domain.weather.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class WeatherCleanupConfig {

    @Bean
    public Job weatherCleanupJob(
        JobRepository jobRepository,
        @Qualifier("weatherCleanupStep") Step step
    ) {
        return new JobBuilder("weatherCleanupJob", jobRepository)
            .start(step)
            .build();
    }

    @Bean
    public Step weatherCleanupStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        WeatherCleanupTasklet weatherCleanupTasklet
    ) {
        return new StepBuilder("weatherCleanupStep", jobRepository)
            .tasklet(weatherCleanupTasklet, transactionManager)
            .build();
    }
}
