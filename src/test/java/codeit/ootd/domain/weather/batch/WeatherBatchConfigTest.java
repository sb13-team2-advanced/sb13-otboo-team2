package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
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

@SpringBootTest
@ActiveProfiles("test")
class WeatherBatchConfigTest {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("weatherCollectionJob")
    private Job weatherCollectionJob;

    @Test
    void weatherCollectionJobCompletes() throws Exception {
        JobExecution execution = jobOperator.start(
            weatherCollectionJob,
            new JobParametersBuilder()
                .addString("requestId", UUID.randomUUID().toString())
                .toJobParameters()
        );

        assertThat(execution.getStatus())
            .isEqualTo(BatchStatus.COMPLETED);
    }
}
