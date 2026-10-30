package codeit.ootd.domain.user.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class InvalidUserQueryException extends OtbooException {

    public InvalidUserQueryException() {
        super(ErrorCode.INVALID_USER_QUERY);
    }
}