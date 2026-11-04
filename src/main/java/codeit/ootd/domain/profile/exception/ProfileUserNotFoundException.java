package codeit.ootd.domain.profile.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ProfileUserNotFoundException extends OtbooException {

    public ProfileUserNotFoundException() {
        super(ErrorCode.PROFILE_USER_NOT_FOUND);
    }
}
