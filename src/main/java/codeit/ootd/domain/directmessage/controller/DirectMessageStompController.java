package codeit.ootd.domain.directmessage.controller;

import codeit.ootd.domain.directmessage.dto.request.DirectMessageSendRequest;
import codeit.ootd.domain.directmessage.service.DirectMessageService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectMessageStompController {

    private final DirectMessageService directMessageService;

    @MessageMapping("/direct-messages_send")
    public void send(@Valid @Payload DirectMessageSendRequest request,
            @AuthenticationPrincipal UUID userId) {
        directMessageService.send(request, userId);
    }
}
