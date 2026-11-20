package codeit.ootd.global.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import codeit.ootd.domain.notification.service.NotificationSseService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

class RealtimePublisherConfigurationTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void selectsRedisPublisherOnlyWhenEnabled(boolean enabled) {
        new ApplicationContextRunner()
                .withUserConfiguration(LocalRealtimeMessagePublisher.class, RedisRealtimeMessagePublisher.class)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(SimpMessagingTemplate.class, () -> mock(SimpMessagingTemplate.class))
                .withBean(NotificationSseService.class, () -> mock(NotificationSseService.class))
                .withPropertyValues("otboo.realtime.redis.enabled=" + enabled)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RealtimeMessagePublisher.class)).isInstanceOf(enabled
                            ? RedisRealtimeMessagePublisher.class : LocalRealtimeMessagePublisher.class);
                    assertThat(context).hasSingleBean(LocalRealtimeMessagePublisher.class);
                });
    }
}
