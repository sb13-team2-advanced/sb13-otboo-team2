package codeit.ootd.domain.clothes.repository;

import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeDefinitionRepository extends JpaRepository<ClothesAttributeDefinition, UUID> {

    boolean existsByName(String name);
}
