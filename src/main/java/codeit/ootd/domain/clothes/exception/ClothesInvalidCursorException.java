package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ClothesInvalidCursorException extends OtbooException {

    public ClothesInvalidCursorException() {
        super(ErrorCode.CLOTHES_INVALID_CURSOR);
    }
}
