package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ClothesInUseException extends OtbooException {

    public ClothesInUseException() {
        super(ErrorCode.CLOTHES_IN_USE);
    }
}
