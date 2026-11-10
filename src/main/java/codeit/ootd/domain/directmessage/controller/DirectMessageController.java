package codeit.ootd.domain.directmessage.controller;

import codeit.ootd.domain.directmessage.dto.DirectMessageDtoCursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DirectMessage", description = "DirectMessage API")
@RestController
@RequestMapping("/api/direct-messages")
public class DirectMessageController {

    @Operation(summary = "DM 목록 조회", operationId = "getDms")
    @GetMapping
    public ResponseEntity<DirectMessageDtoCursorResponse> getDms(
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
