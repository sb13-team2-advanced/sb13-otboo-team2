package codeit.ootd.domain.directmessage.event;

import codeit.ootd.domain.directmessage.dto.DirectMessageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DirectMessageEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DirectMessageSentEvent event) {
        DirectMessageDto message = event.message();
        // 프런트 및 구독 권한 검사와 동일하게 UUID 문자열의 사전순으로 정렬한다.
        String sender = message.sender().userId().toString();
        String receiver = message.receiver().userId().toString();
        String participants = sender.compareTo(receiver) <= 0
                ? sender + "_" + receiver : receiver + "_" + sender;
        messagingTemplate.convertAndSend("/sub/direct-messages_" + participants, message);
    }
}
