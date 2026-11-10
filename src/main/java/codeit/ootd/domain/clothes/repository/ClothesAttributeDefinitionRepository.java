package codeit.ootd.domain.clothes.repository;

import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClothesAttributeDefinitionRepository extends JpaRepository<ClothesAttributeDefinition, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<ClothesAttributeDefinition> findAllByNameContainingIgnoreCase(String keyword, Sort sort);

    /**
     * 선택값(지연 로딩)을 함께 읽어, 트랜잭션 밖에서도 접근할 수 있게 한다.
     */
    @Query("select distinct d from ClothesAttributeDefinition d left join fetch d.selectableValues")
    List<ClothesAttributeDefinition> findAllWithSelectableValues();
}
