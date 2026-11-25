package codeit.ootd.domain.notification.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class NotificationInvalidCursorException extends OtbooException {

    public NotificationInvalidCursorException() {
        super(ErrorCode.NOTIFICATION_INVALID_CURSOR);
    }
}
