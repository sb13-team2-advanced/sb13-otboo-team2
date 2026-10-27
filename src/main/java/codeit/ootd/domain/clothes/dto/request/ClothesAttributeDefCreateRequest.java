package codeit.ootd.domain.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClothesAttributeDefCreateRequest(

        @NotBlank(message = "속성 정의 이름은 필수입니다.")
        @Size(max = 50, message = "속성 정의 이름은 50자 이하여야 합니다.")
        String name,

        @NotEmpty(message = "선택 가능한 값은 1개 이상이어야 합니다.")
        List<@NotBlank(message = "선택 가능한 값은 비어 있을 수 없습니다.")
                @Size(max = 50, message = "선택 가능한 값은 50자 이하여야 합니다.") String> selectableValues
) {
}
