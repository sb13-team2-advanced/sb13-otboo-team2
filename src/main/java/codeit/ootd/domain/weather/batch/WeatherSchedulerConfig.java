package codeit.ootd.domain.weather.batch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class WeatherSchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler weatherCollectionScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("weather-collection-");
        return scheduler;
    }

    @Bean
    public ThreadPoolTaskScheduler weatherCleanupTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("weather-cleanup-");
        return scheduler;
    }
}
