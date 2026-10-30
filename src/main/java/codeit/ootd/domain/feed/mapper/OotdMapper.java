package codeit.ootd.domain.feed.mapper;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OotdMapper {

    @Mapping(target = "clothesId", source = "clothes.id")
    OotdDto toDto(Clothes clothes, List<ClothesAttributeWithDefDto> attributes);

    @Mapping(target = "definitionId", source = "definition.id")
    @Mapping(target = "definitionName", source = "definition.name")
    @Mapping(target = "selectableValues", source = "definition.selectableValues")
    @Mapping(target = "value", source = "attribute.value")
    ClothesAttributeWithDefDto toAttributeDto(
            ClothesAttribute attribute,
            ClothesAttributeDefinition definition
    );
}