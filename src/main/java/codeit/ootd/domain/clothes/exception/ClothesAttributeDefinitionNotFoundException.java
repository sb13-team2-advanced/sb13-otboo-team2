package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ClothesAttributeDefinitionNotFoundException extends OtbooException {

    public ClothesAttributeDefinitionNotFoundException() {
        super(ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND);
    }
}
