package codeit.ootd.domain.user.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class UserNotFoundException extends OtbooException {

    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}