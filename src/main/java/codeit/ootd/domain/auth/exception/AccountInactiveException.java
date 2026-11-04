package codeit.ootd.domain.auth.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class AccountInactiveException extends OtbooException {

    public AccountInactiveException() {
        super(ErrorCode.ACCOUNT_INACTIVE);
    }
}