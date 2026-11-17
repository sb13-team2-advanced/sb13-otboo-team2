package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class WeatherBatchConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ootd_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("weatherCollectionJob")
    private Job weatherCollectionJob;

    @MockitoBean
    private WeatherLocationRepository weatherLocationRepository;

    @Test
    @DisplayName("동일한 실행 구간의 Batch는 실행 중이거나 완료된 경우 중복 실행되지 않는다")
    void sameRunSlotDoesNotExecuteTwice() throws Exception {
        CountDownLatch collectionStarted = new CountDownLatch(1);
        CountDownLatch releaseCollection = new CountDownLatch(1);

        when(weatherLocationRepository
            .findTop20ByResolutionAndLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                eq((short) 7),
                any(Instant.class)
            ))
            .thenAnswer(invocation -> {
                collectionStarted.countDown();

                if (!releaseCollection.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Batch 대기 시간 초과");
                }

                return List.of();
            });

        long runSlot = Instant.now().getEpochSecond() / 600;

        JobParameters parameters = new JobParametersBuilder()
            .addLong("runSlot", runSlot)
            .toJobParameters();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<JobExecution> firstExecution = executor.submit(
                () -> jobOperator.start(weatherCollectionJob, parameters)
            );

            assertThat(collectionStarted.await(10, TimeUnit.SECONDS))
                .isTrue();

            assertThatThrownBy(
                () -> jobOperator.start(weatherCollectionJob, parameters)
            )
                .isInstanceOf(JobExecutionAlreadyRunningException.class);

            releaseCollection.countDown();

            JobExecution completedExecution =
                firstExecution.get(10, TimeUnit.SECONDS);

            assertThat(completedExecution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);

            assertThatThrownBy(
                () -> jobOperator.start(weatherCollectionJob, parameters)
            )
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);

            verify(weatherLocationRepository, times(1))
                .findTop20ByResolutionAndLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                    eq((short) 7),
                    any(Instant.class)
                );
        } finally {
            releaseCollection.countDown();
            executor.shutdownNow();
        }
    }
}