package codeit.ootd.domain.directmessage.controller;

import codeit.ootd.domain.directmessage.dto.DirectMessageDtoCursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import codeit.ootd.domain.directmessage.service.DirectMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DirectMessage", description = "DirectMessage API")
@RestController
@RequestMapping("/api/direct-messages")
@RequiredArgsConstructor
public class DirectMessageController {

    private final DirectMessageService directMessageService;

    @Operation(summary = "DM 목록 조회", operationId = "getDms")
    @GetMapping
    public ResponseEntity<DirectMessageDtoCursorResponse> getDms(
            @AuthenticationPrincipal UUID currentUserId,
            @RequestParam UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam int limit
    ) {
        return ResponseEntity.ok(directMessageService.getMessages(
                currentUserId, userId, cursor, idAfter, limit));
    }
}
