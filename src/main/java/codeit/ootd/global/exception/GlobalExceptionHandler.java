package codeit.ootd.global.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OtbooException.class)
    public ResponseEntity<ErrorResponse> handleOtbooException(OtbooException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse errorResponse = ErrorResponse.from(exception, errorCode);

        log.warn("Otboo exception: code={}, message={}", errorCode, exception.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(errorResponse);
    }

    /**
     * @Valid 검증에 실패하면 필드별 실패 사유를 details에 담아 돌려준다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;

        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(),
                    fieldError.getDefaultMessage() == null ? "" : fieldError.getDefaultMessage());
        }

        log.warn("Validation failed: fields={}", details.keySet());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(exception, errorCode, details));
    }

    /**
     * 필수 값 누락, JSON 파싱 실패, 타입 변환 실패처럼 요청 형식 자체가 잘못된 경우를 같은 형식으로 응답한다.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception exception) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;

        log.warn("Malformed request: type={}", exception.getClass().getSimpleName());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(exception, errorCode));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        ErrorCode errorCode = ErrorCode.FILE_TOO_LARGE;

        log.warn("Upload size exceeded");

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(exception, errorCode));
    }
}
