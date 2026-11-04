package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ClothesAttributeValuesDuplicatedException extends OtbooException {

    public ClothesAttributeValuesDuplicatedException() {
        super(ErrorCode.CLOTHES_ATTRIBUTE_VALUES_DUPLICATED);
    }
}
