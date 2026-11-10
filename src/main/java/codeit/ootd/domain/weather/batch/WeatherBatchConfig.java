package codeit.ootd.domain.weather.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

@Slf4j
@Configuration
public class WeatherBatchConfig {

    @Bean
    public Job weatherCollectionJob(
        JobRepository jobRepository,
        @Qualifier("weatherCollectionStep") Step step
    ) {
        return new JobBuilder("weatherCollectionJob", jobRepository)
            .start(step)
            .build();
    }

    @Bean
    public Step weatherCollectionStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        WeatherCollectionTasklet weatherCollectionTasklet
    ) {

        DefaultTransactionAttribute attribute = new DefaultTransactionAttribute();

        attribute.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        );

        return new StepBuilder("weatherCollectionStep", jobRepository)
            .tasklet(weatherCollectionTasklet, transactionManager)
            .transactionAttribute(attribute)
            .build();
    }
}
