package codeit.ootd.domain.notification.controller;

import codeit.ootd.domain.notification.service.NotificationSseService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class NotificationSseController {

  private final NotificationSseService notificationSseService;

  @GetMapping(value = "/api/sse",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @AuthenticationPrincipal UUID userId
  ) {
    return notificationSseService.subscribe(userId);
  }
}
