package codeit.ootd.domain.follow.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class FollowNotFoundException extends OtbooException {

    public FollowNotFoundException() {
        super(ErrorCode.FOLLOW_NOT_FOUND);
    }
}
