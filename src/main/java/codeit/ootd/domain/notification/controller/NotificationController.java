package codeit.ootd.domain.notification.controller;

import codeit.ootd.domain.notification.dto.response.NotificationDtoCursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림", description = "알림 API")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Operation(summary = "알림 목록 조회", operationId = "getNotifications")
    @GetMapping
    public ResponseEntity<NotificationDtoCursorResponse> getNotifications(
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Operation(summary = "알림 읽음 처리", operationId = "readNotifications")
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> readNotifications(
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
