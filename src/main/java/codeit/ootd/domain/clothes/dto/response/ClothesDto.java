package codeit.ootd.domain.clothes.dto.response;

import codeit.ootd.domain.clothes.enums.ClothesType;
import java.util.List;
import java.util.UUID;

public record ClothesDto(
        UUID id,
        UUID ownerId,
        String name,
        String imageUrl,
        ClothesType type,
        List<ClothesAttributeWithDefDto> attributes
) {
}
