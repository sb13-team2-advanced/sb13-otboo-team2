package codeit.ootd.domain.follow.exception;

import codeit.ootd.global.exception.OtbooException;
import codeit.ootd.global.exception.ErrorCode;

public class FollowAlreadyExistsException extends OtbooException {

    public FollowAlreadyExistsException() {
        super(ErrorCode.FOLLOW_ALREADY_EXISTS);
    }
}
