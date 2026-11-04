package codeit.ootd.domain.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 값이 null인 항목은 변경하지 않는다.
 */
public record ClothesAttributeDefUpdateRequest(

        @Size(max = 50, message = "속성 정의 이름은 50자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "속성 정의 이름은 비어 있을 수 없습니다.")
        String name,

        @Size(min = 1, message = "선택 가능한 값은 1개 이상이어야 합니다.")
        List<@NotBlank(message = "선택 가능한 값은 비어 있을 수 없습니다.")
                @Size(max = 50, message = "선택 가능한 값은 50자 이하여야 합니다.") String> selectableValues
) {
}
