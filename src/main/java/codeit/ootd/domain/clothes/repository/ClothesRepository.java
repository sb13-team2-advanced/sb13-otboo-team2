package codeit.ootd.domain.clothes.repository;

import codeit.ootd.domain.clothes.entity.Clothes;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothesRepository extends JpaRepository<Clothes, UUID> {

    /**
     * 피드에 포함된 의상인지 확인한다. 삭제 가능 여부를 판단할 때 사용한다.
     */
    @Query("select count(fc) > 0 from FeedClothes fc where fc.clothesId = :clothesId")
    boolean existsInFeed(@Param("clothesId") UUID clothesId);
}
