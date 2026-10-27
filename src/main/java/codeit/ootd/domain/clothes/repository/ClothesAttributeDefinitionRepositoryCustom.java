package codeit.ootd.domain.clothes.repository;

import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesAttributeDefSortBy;
import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public interface ClothesAttributeDefinitionRepositoryCustom {

    /**
     * 정렬 값이 같을 때를 대비해 id를 보조 정렬 기준으로 쓰는 커서 조회.
     * cursorValue와 idAfter는 둘 다 null이거나 둘 다 있어야 한다.
     */
    List<ClothesAttributeDefinition> findPage(
            String keyword,
            ClothesAttributeDefSortBy sortBy,
            SortDirection direction,
            Object cursorValue,
            UUID idAfter,
            int size);

    long countByKeyword(String keyword);
}
