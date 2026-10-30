package codeit.ootd.domain.auth.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class InvalidAuthSessionException extends OtbooException {

    public InvalidAuthSessionException() {
        super(ErrorCode.INVALID_AUTH_SESSION);
    }
}