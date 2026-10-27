package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class InvalidCursorException extends OtbooException {

    public InvalidCursorException() {
        super(ErrorCode.INVALID_CURSOR);
    }
}
