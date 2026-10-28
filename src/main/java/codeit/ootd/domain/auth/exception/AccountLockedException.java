package codeit.ootd.domain.auth.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class AccountLockedException extends OtbooException {

    public AccountLockedException() {
        super(ErrorCode.ACCOUNT_LOCKED);
    }
}
