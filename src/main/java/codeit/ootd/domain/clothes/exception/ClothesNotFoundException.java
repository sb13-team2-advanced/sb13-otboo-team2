package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ClothesNotFoundException extends OtbooException {

    public ClothesNotFoundException() {
        super(ErrorCode.CLOTHES_NOT_FOUND);
    }
}
