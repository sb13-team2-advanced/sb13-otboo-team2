package codeit.ootd.domain.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ClothesAttributeRequest(

        @NotNull(message = "속성 정의 ID는 필수입니다.")
        UUID definitionId,

        @NotBlank(message = "속성 값은 필수입니다.")
        @Size(max = 50, message = "속성 값은 50자 이하여야 합니다.")
        String value
) {
}
