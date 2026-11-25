package codeit.ootd.domain.directmessage.controller;

import codeit.ootd.domain.directmessage.dto.request.DirectMessageSendRequest;
import codeit.ootd.domain.directmessage.service.DirectMessageService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.ErrorResponse;
import codeit.ootd.global.exception.OtbooException;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DirectMessageStompController {

    private final DirectMessageService directMessageService;

    @MessageMapping("/direct-messages_send")
    public void send(@Valid @Payload DirectMessageSendRequest request,
            @AuthenticationPrincipal UUID userId) {
        directMessageService.send(request, userId);
    }

    @MessageExceptionHandler(OtbooException.class)
    @SendToUser(value = "/sub/direct-messages_errors", broadcast = false)
    public ErrorResponse handleOtbooException(OtbooException exception) {
        log.warn("DM 전송 거부: code={}", exception.getErrorCode());
        return ErrorResponse.from(exception, exception.getErrorCode());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(value = "/sub/direct-messages_errors", broadcast = false)
    public ErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        if (exception.getBindingResult() != null) {
            exception.getBindingResult().getFieldErrors().forEach(error ->
                    details.putIfAbsent(error.getField(),
                            error.getDefaultMessage() == null ? "" : error.getDefaultMessage()));
        }
        log.warn("DM 메시지 검증 실패: fields={}", details.keySet());
        return ErrorResponse.of(exception, ErrorCode.INVALID_REQUEST, details);
    }

    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(value = "/sub/direct-messages_errors", broadcast = false)
    public ErrorResponse handleMalformedMessage(MessageConversionException exception) {
        log.warn("DM 메시지 변환 실패: type={}", exception.getClass().getSimpleName());
        return ErrorResponse.from(exception, ErrorCode.INVALID_REQUEST);
    }
}
