package codeit.ootd.domain.clothes.enums;

import codeit.ootd.domain.clothes.exception.InvalidSortFieldException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 속성 정의 목록의 정렬 기준.
 */
@Getter
@RequiredArgsConstructor
public enum ClothesAttributeDefSortBy {

    NAME("name"),
    CREATED_AT("createdAt");

    private final String property;

    public static ClothesAttributeDefSortBy from(String value) {
        if (value == null || value.isBlank()) {
            return CREATED_AT;
        }
        for (ClothesAttributeDefSortBy sortBy : values()) {
            if (sortBy.property.equals(value)) {
                return sortBy;
            }
        }
        throw new InvalidSortFieldException();
    }
}
