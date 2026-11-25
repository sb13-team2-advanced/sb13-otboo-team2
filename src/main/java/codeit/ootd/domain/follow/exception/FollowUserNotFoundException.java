package codeit.ootd.domain.follow.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class FollowUserNotFoundException extends OtbooException {

    public FollowUserNotFoundException() {
        super(ErrorCode.FOLLOW_USER_NOT_FOUND);
    }
}
