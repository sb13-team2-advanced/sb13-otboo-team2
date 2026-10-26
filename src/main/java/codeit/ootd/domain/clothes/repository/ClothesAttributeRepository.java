package codeit.ootd.domain.clothes.repository;

import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeRepository extends JpaRepository<ClothesAttribute, UUID> {

    List<ClothesAttribute> findAllByClothesId(UUID clothesId);
}
