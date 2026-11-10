package codeit.ootd.domain.notification.controller;

import codeit.ootd.domain.notification.dto.response.NotificationDtoCursorResponse;
import codeit.ootd.domain.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림", description = "알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회", operationId = "getNotifications")
    @GetMapping
    public ResponseEntity<NotificationDtoCursorResponse> getNotifications(
        @AuthenticationPrincipal UUID userId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) UUID idAfter,
        @RequestParam int limit
    ) {
        return ResponseEntity.ok(
            notificationService.getNotifications(userId, cursor, idAfter, limit)
        );
    }

    @Operation(summary = "알림 읽음 처리", operationId = "readNotifications")
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> readNotifications(
        @PathVariable UUID notificationId,
        @AuthenticationPrincipal UUID userId
    ) {
        notificationService.readNotification(notificationId, userId);
        return ResponseEntity.noContent().build();
    }
}
