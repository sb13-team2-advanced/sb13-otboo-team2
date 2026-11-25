package codeit.ootd.domain.directmessage.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class DirectMessageUserNotFoundException extends OtbooException {

    public DirectMessageUserNotFoundException() {
        super(ErrorCode.DIRECT_MESSAGE_USER_NOT_FOUND);
    }
}
