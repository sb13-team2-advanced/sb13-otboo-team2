package codeit.ootd.domain.notification.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "otboo.notification.kafka.enabled", havingValue = "true")
public class NotificationKafkaConfig {
    @Bean
    public ThreadPoolTaskScheduler notificationKafkaScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-kafka-");
        return scheduler;
    }
}
