package codeit.ootd.domain.user.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class DuplicateEmailException extends OtbooException {

    public DuplicateEmailException() {
        super(ErrorCode.USER_EMAIL_DUPLICATED);
    }
}
