package codeit.ootd.domain.follow.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class FollowSelfNotAllowedException extends OtbooException {

    public FollowSelfNotAllowedException() {
        super(ErrorCode.FOLLOW_SELF_NOT_ALLOWED);
    }
}
