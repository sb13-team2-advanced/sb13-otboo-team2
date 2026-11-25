package codeit.ootd.domain.directmessage.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class DirectMessageInvalidCursorException extends OtbooException {

    public DirectMessageInvalidCursorException() {
        super(ErrorCode.DIRECT_MESSAGE_INVALID_CURSOR);
    }
}
