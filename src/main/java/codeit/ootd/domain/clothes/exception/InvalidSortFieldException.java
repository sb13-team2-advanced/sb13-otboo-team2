package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class InvalidSortFieldException extends OtbooException {

    public InvalidSortFieldException() {
        super(ErrorCode.INVALID_SORT_FIELD);
    }
}
