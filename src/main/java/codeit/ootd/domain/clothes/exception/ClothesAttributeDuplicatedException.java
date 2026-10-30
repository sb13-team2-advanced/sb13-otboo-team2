package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ClothesAttributeDuplicatedException extends OtbooException {

    public ClothesAttributeDuplicatedException() {
        super(ErrorCode.CLOTHES_ATTRIBUTE_DUPLICATED);
    }
}
