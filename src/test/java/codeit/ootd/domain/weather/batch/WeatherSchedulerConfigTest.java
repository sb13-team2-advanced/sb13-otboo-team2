package codeit.ootd.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;

class WeatherSchedulerConfigTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(WeatherSchedulerConfig.class);

    @Test
    @DisplayName("날씨 수집과 정리 스케줄러를 독립적인 Bean으로 등록한다")
    void registersIndependentSchedulers() {
        contextRunner.run(context -> {
            TaskScheduler collectionScheduler =
                context.getBean(
                    "weatherCollectionScheduler",
                    TaskScheduler.class
                );

            TaskScheduler cleanupScheduler =
                context.getBean(
                    "weatherCleanupTaskScheduler",
                    TaskScheduler.class
                );

            assertThat(collectionScheduler)
                .isNotSameAs(cleanupScheduler);
        });
    }

    @Test
    @DisplayName("날씨 수집과 정리 Batch가 각각 지정된 스케줄러를 사용한다")
    void scheduledJobsUseSeparateSchedulers() throws NoSuchMethodException {
        Scheduled collectionScheduled = WeatherBatchScheduler.class
            .getMethod("run")
            .getAnnotation(Scheduled.class);

        Scheduled cleanupScheduled = WeatherCleanupScheduler.class
            .getMethod("run")
            .getAnnotation(Scheduled.class);

        assertThat(collectionScheduled).isNotNull();
        assertThat(cleanupScheduled).isNotNull();

        assertThat(collectionScheduled.scheduler())
            .isEqualTo("weatherCollectionScheduler");

        assertThat(cleanupScheduled.scheduler())
            .isEqualTo("weatherCleanupTaskScheduler");

        contextRunner.run(context -> {
            TaskScheduler collectionScheduler = context.getBean(
                collectionScheduled.scheduler(),
                TaskScheduler.class
            );

            TaskScheduler cleanupScheduler = context.getBean(
                cleanupScheduled.scheduler(),
                TaskScheduler.class
            );

            assertThat(collectionScheduler)
                .isNotSameAs(cleanupScheduler);
        });
    }
}
