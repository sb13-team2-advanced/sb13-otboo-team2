package codeit.ootd.domain.auth.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class PasswordResetRateLimitExceededException
        extends OtbooException {

    public PasswordResetRateLimitExceededException() {
        super(ErrorCode.PASSWORD_RESET_RATE_LIMIT_EXCEEDED);
    }
}