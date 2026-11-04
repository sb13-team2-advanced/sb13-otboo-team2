package codeit.ootd.domain.clothes.repository;

import codeit.ootd.domain.clothes.entity.Clothes;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothesRepository extends JpaRepository<Clothes, UUID> {

    /**
     * 피드에 포함된 의상인지 확인한다. 삭제 가능 여부를 판단할 때 사용한다.
     */
    @Query("select count(fc) > 0 from FeedClothes fc where fc.clothesId = :clothesId")
    boolean existsInFeed(@Param("clothesId") UUID clothesId);


    /**
     * Spring Data JPA의 메서드 이름 기반 쿼리로 사용자 소유 의상을 모두 조회한다.
     *
     * @param userId 의상 소유자 ID
     * @return 사용자가 소유한 의상 목록. 등록된 의상이 없으면 빈 목록
     */
    List<Clothes> findAllByUserId(UUID userId);
}
