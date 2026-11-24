package codeit.ootd.global.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "otboo.realtime.redis.enabled", havingValue = "true")
public class RedisRealtimeConfig {
    @Bean
    RedisMessageListenerContainer realtimeRedisListenerContainer(RedisConnectionFactory connectionFactory,
            RedisRealtimeMessagePublisher listener) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(listener.directMessageChannel()));
        container.addMessageListener(listener, new ChannelTopic(listener.notificationChannel()));
        container.setErrorHandler(error -> log.error("실시간 Redis 메시지 처리 실패", error));
        return container;
    }
}
