package codeit.ootd.domain.auth.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class SocialAccountAlreadyLinkedException extends OtbooException {

    public SocialAccountAlreadyLinkedException() {
        super(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
    }
}