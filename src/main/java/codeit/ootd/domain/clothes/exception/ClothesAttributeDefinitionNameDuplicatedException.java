package codeit.ootd.domain.clothes.exception;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

public class ClothesAttributeDefinitionNameDuplicatedException extends OtbooException {

    public ClothesAttributeDefinitionNameDuplicatedException() {
        super(ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NAME_DUPLICATED);
    }
}
