package codeit.ootd.domain.clothes.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * 의상 속성 값과 속성 정의 정보. 의상 응답과 피드의 OOTD 응답에서 함께 사용한다.
 */
public record ClothesAttributeWithDefDto(
    UUID definitionId,
    String definitionName,
    List<String> selectableValues,
    String value
) {
}
