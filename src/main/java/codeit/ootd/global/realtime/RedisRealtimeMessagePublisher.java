package codeit.ootd.global.realtime;

import codeit.ootd.domain.directmessage.dto.DirectMessageDto;
import codeit.ootd.domain.notification.dto.response.NotificationDto;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "otboo.realtime.redis.enabled", havingValue = "true")
public class RedisRealtimeMessagePublisher implements RealtimeMessagePublisher, MessageListener {
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final LocalRealtimeMessagePublisher local;
    private final String directMessageChannel;
    private final String notificationChannel;

    public RedisRealtimeMessagePublisher(StringRedisTemplate redis, JsonMapper jsonMapper,
            LocalRealtimeMessagePublisher local,
            @Value("${otboo.realtime.redis.channel-prefix:otboo:realtime:v1}") String channelPrefix) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.local = local;
        this.directMessageChannel = channelPrefix + ":direct-messages";
        this.notificationChannel = channelPrefix + ":notifications";
    }

    @Override
    public void publishDirectMessage(DirectMessageDto message) {
        publish(directMessageChannel, message);
    }

    @Override
    public void publishNotification(NotificationDto notification) {
        publish(notificationChannel, notification);
    }

    private void publish(String channel, Object payload) {
        try {
            // 발행 서버도 Redis 구독을 통해서만 전달하여 로컬 중복 전송을 방지한다.
            redis.convertAndSend(channel, jsonMapper.writeValueAsString(payload));
        } catch (RuntimeException exception) {
            // DB 커밋은 끝났으므로 전송 실패가 후속 알림 처리를 막지 않게 한다. 이력은 조회 API로 복구한다.
            log.error("실시간 Redis 발행 실패: channel={}", channel, exception);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        if (directMessageChannel.equals(channel)) {
            local.publishDirectMessage(jsonMapper.readValue(payload, DirectMessageDto.class));
        } else if (notificationChannel.equals(channel)) {
            local.publishNotification(jsonMapper.readValue(payload, NotificationDto.class));
        }
    }

    String directMessageChannel() {
        return directMessageChannel;
    }

    String notificationChannel() {
        return notificationChannel;
    }
}
