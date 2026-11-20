package codeit.ootd.global.realtime;

import codeit.ootd.domain.directmessage.dto.DirectMessageDto;
import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.service.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalRealtimeMessagePublisher implements RealtimeMessagePublisher {
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationSseService sse;

    @Override
    public void publishDirectMessage(DirectMessageDto message) {
        // 프런트 및 구독 권한 검사와 동일하게 UUID 문자열의 사전순으로 정렬한다.
        String sender = message.sender().userId().toString();
        String receiver = message.receiver().userId().toString();
        String participants = sender.compareTo(receiver) <= 0
                ? sender + "_" + receiver : receiver + "_" + sender;
        messagingTemplate.convertAndSend("/sub/direct-messages_" + participants, message);
    }

    @Override
    public void publishNotification(NotificationDto notification) {
        sse.send(notification.receiverId(), notification);
    }
}
