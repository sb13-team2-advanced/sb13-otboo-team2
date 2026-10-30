package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ClothesOwnerNotFoundException extends OtbooException {

    public ClothesOwnerNotFoundException() {
        super(ErrorCode.CLOTHES_OWNER_NOT_FOUND);
    }
}
