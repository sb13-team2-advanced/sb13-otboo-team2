package codeit.ootd.domain.follow.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class FollowInvalidCursorException extends OtbooException {

    public FollowInvalidCursorException() {
        super(ErrorCode.FOLLOW_INVALID_CURSOR);
    }
}
