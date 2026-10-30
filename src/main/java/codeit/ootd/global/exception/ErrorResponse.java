package codeit.ootd.global.exception;

import java.util.Map;

public record ErrorResponse(String exceptionName, String message, Map<String, String> details) {

    public static ErrorResponse from(Exception exception, ErrorCode errorCode) {
        return new ErrorResponse(exception.getClass().getSimpleName(), errorCode.getMessage(), Map.of());
    }

    public static ErrorResponse of(Exception exception, ErrorCode errorCode, Map<String, String> details) {
        return new ErrorResponse(exception.getClass().getSimpleName(), errorCode.getMessage(), details);
    }
}
