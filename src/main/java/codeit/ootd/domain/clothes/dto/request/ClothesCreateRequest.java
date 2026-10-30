package codeit.ootd.domain.clothes.dto.request;

import codeit.ootd.domain.clothes.enums.ClothesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ClothesCreateRequest(

        @NotNull(message = "소유자 ID는 필수입니다.")
        UUID ownerId,

        @NotBlank(message = "의상 이름은 필수입니다.")
        @Size(max = 50, message = "의상 이름은 50자 이하여야 합니다.")
        String name,

        @NotNull(message = "의상 타입은 필수입니다.")
        ClothesType type,

        List<@NotNull(message = "속성 항목은 비어 있을 수 없습니다.") @Valid ClothesAttributeRequest> attributes
) {
}
