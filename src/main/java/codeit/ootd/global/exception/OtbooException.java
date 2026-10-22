package codeit.ootd.global.exception;

import java.util.Objects;
import lombok.Getter;

@Getter
public class OtbooException extends RuntimeException {

    private final ErrorCode errorCode;

    public OtbooException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode).getMessage());
        this.errorCode = errorCode;
    }
}
