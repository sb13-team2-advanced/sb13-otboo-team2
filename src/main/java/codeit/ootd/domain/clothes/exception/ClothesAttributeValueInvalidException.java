package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ClothesAttributeValueInvalidException extends OtbooException {

    public ClothesAttributeValueInvalidException() {
        super(ErrorCode.CLOTHES_ATTRIBUTE_VALUE_INVALID);
    }
}
