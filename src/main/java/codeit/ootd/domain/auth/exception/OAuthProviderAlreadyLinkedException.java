package codeit.ootd.domain.auth.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class OAuthProviderAlreadyLinkedException extends OtbooException {

    public OAuthProviderAlreadyLinkedException() {
        super(ErrorCode.OAUTH_PROVIDER_ALREADY_LINKED);
    }
}
