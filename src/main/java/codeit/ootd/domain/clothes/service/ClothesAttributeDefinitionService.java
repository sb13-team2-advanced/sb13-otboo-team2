package codeit.ootd.domain.clothes.service;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDto;
import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public interface ClothesAttributeDefinitionService {

    ClothesAttributeDefDto create(ClothesAttributeDefCreateRequest request);

    List<ClothesAttributeDefDto> list(String sortBy, SortDirection sortDirection, String keywordLike);

    ClothesAttributeDefDto update(UUID definitionId, ClothesAttributeDefUpdateRequest request);

    void delete(UUID definitionId);
}
