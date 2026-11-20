package codeit.ootd.global.realtime;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
class RedisRealtimeIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Test
    void twoServersReceiveDmAndNotificationExactlyOnceIncludingPublishingServer() throws Exception {
        var firstConnection = connection();
        var secondConnection = connection();
        RedisMessageListenerContainer firstContainer = null;
        RedisMessageListenerContainer secondContainer = null;
        try {
            var firstLocal = mock(LocalRealtimeMessagePublisher.class);
            var secondLocal = mock(LocalRealtimeMessagePublisher.class);
            var mapper = JsonMapper.builder().build();
            var first = new RedisRealtimeMessagePublisher(new StringRedisTemplate(firstConnection),
                    mapper, firstLocal, "integration:realtime");
            var second = new RedisRealtimeMessagePublisher(new StringRedisTemplate(secondConnection),
                    mapper, secondLocal, "integration:realtime");
            var config = new RedisRealtimeConfig();
            firstContainer = config.realtimeRedisListenerContainer(firstConnection, first);
            secondContainer = config.realtimeRedisListenerContainer(secondConnection, second);
            firstContainer.afterPropertiesSet();
            secondContainer.afterPropertiesSet();
            firstContainer.start();
            secondContainer.start();

            var dm = RedisRealtimeMessagePublisherTest.directMessage();
            var notification = RedisRealtimeMessagePublisherTest.notification();
            first.publishDirectMessage(dm);
            second.publishNotification(notification);

            verify(firstLocal, timeout(5000)).publishDirectMessage(dm);
            verify(secondLocal, timeout(5000)).publishDirectMessage(dm);
            verify(firstLocal, timeout(5000)).publishNotification(notification);
            verify(secondLocal, timeout(5000)).publishNotification(notification);
            firstContainer.stop();
            secondContainer.stop();
            verifyNoMoreInteractions(firstLocal, secondLocal);
        } finally {
            if (firstContainer != null) firstContainer.destroy();
            if (secondContainer != null) secondContainer.destroy();
            firstConnection.destroy();
            secondConnection.destroy();
        }
    }

    private LettuceConnectionFactory connection() {
        var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}
