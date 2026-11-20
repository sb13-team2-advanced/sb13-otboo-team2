package codeit.ootd.global.realtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.directmessage.dto.DirectMessageDto;
import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.user.dto.response.UserSummary;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class RedisRealtimeMessagePublisherTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final LocalRealtimeMessagePublisher local = mock(LocalRealtimeMessagePublisher.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RedisRealtimeMessagePublisher publisher =
            new RedisRealtimeMessagePublisher(redis, mapper, local, "test:realtime");

    @Test
    void redisFailureDoesNotInterruptPostCommitProcessingOrSendOnlyLocally() {
        when(redis.convertAndSend(anyString(), anyString())).thenThrow(new IllegalStateException("offline"));
        assertThatCode(() -> publisher.publishDirectMessage(directMessage())).doesNotThrowAnyException();
        assertThatCode(() -> publisher.publishNotification(notification())).doesNotThrowAnyException();
        verifyNoInteractions(local);
    }

    @Test
    void ignoresOtherChannelsAndRejectsMalformedPayload() {
        publisher.onMessage(message("other:notifications", "{}"), null);
        assertThatThrownBy(() -> publisher.onMessage(message("test:realtime:notifications", "invalid"), null))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(local, redis);
    }

    static DirectMessageDto directMessage() {
        return new DirectMessageDto(UUID.randomUUID(), Instant.now(),
                new UserSummary(UUID.randomUUID(), "발신자", null),
                new UserSummary(UUID.randomUUID(), "수신자", null), "안녕하세요");
    }

    static NotificationDto notification() {
        return new NotificationDto(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                "새 DM", "DM이 도착했습니다.", NotificationLevel.INFO);
    }

    private Message message(String channel, String payload) {
        return new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8));
    }
}
