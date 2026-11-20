package codeit.ootd.domain.auth.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class SocialAccountPasswordResetNotAllowedException
        extends OtbooException {

  public SocialAccountPasswordResetNotAllowedException() {
    super(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_RESET_NOT_ALLOWED);
  }
}
