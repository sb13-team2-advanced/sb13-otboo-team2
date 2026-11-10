package codeit.ootd.domain.clothes.dto.request;

import codeit.ootd.domain.clothes.enums.ClothesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 부분 수정 요청. null인 필드는 기존 값을 유지하고, attributes가 있으면 속성 전체를 요청 내용으로 맞춘다.
 */
public record ClothesUpdateRequest(

        @Size(max = 50, message = "의상 이름은 50자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "의상 이름은 비어 있을 수 없습니다.")
        String name,

        ClothesType type,

        List<@NotNull(message = "속성 항목은 비어 있을 수 없습니다.") @Valid ClothesAttributeRequest> attributes
) {
}
