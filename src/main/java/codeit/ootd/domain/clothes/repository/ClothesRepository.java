package codeit.ootd.domain.clothes.repository;

import codeit.ootd.domain.clothes.entity.Clothes;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesRepository extends JpaRepository<Clothes, UUID> {
}
