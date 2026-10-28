package codeit.ootd.domain.clothes.dto.response;

import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClothesAttributeDefDto(
        UUID id,
        Instant createdAt,
        String name,
        List<String> selectableValues
) {

    public static ClothesAttributeDefDto from(ClothesAttributeDefinition definition) {
        return new ClothesAttributeDefDto(
                definition.getId(),
                definition.getCreatedAt(),
                definition.getName(),
                List.copyOf(definition.getSelectableValues())
        );
    }
}
