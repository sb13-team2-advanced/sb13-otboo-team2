package codeit.ootd.domain.clothes.enums;

import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.exception.InvalidCursorException;
import codeit.ootd.domain.clothes.exception.InvalidSortFieldException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 속성 정의 목록의 정렬 기준. 커서 값의 생성·해석도 정렬 기준마다 다르므로 함께 둔다.
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

    /** 요청으로 받은 커서 문자열을 쿼리 비교에 쓸 값으로 바꾼다. */
    public Object parseCursor(String cursor) {
        if (this == NAME) {
            return cursor;
        }
        try {
            return Instant.parse(cursor);
        } catch (DateTimeParseException e) {
            throw new InvalidCursorException();
        }
    }

    /** 마지막 항목에서 다음 요청에 쓸 커서 문자열을 만든다. */
    public String cursorOf(ClothesAttributeDefinition definition) {
        if (this == NAME) {
            return definition.getName();
        }
        return definition.getCreatedAt().toString();
    }
}
