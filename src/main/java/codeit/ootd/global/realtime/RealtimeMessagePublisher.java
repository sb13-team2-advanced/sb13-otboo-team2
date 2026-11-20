package codeit.ootd.global.realtime;

import codeit.ootd.domain.directmessage.dto.DirectMessageDto;
import codeit.ootd.domain.notification.dto.response.NotificationDto;

public interface RealtimeMessagePublisher {
    void publishDirectMessage(DirectMessageDto message);

    void publishNotification(NotificationDto notification);
}
