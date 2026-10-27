package codeit.ootd.domain.clothes.repository;

import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeDefinitionRepository extends JpaRepository<ClothesAttributeDefinition, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<ClothesAttributeDefinition> findAllByNameContainingIgnoreCase(String keyword, Sort sort);
}
