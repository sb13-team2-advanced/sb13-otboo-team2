package codeit.ootd.domain.directmessage.event;

import codeit.ootd.global.realtime.RealtimeMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DirectMessageEventListener {

    private final RealtimeMessagePublisher realtime;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DirectMessageSentEvent event) {
        realtime.publishDirectMessage(event.message());
    }
}
