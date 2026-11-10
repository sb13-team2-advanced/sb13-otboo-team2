package codeit.ootd.domain.clothes.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 의상·속성 정의·의상 속성 엔티티의 저장·조회 매핑과 중복 제약을 검증한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ClothesMappingTest {

    @Autowired
    private ClothesRepository clothesRepository;

    @Autowired
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Autowired
    private ClothesAttributeRepository attributeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 의상은_이미지_없이_저장되고_시간이_기록된다() {
        // given
        UUID userId = UUID.randomUUID();
        UUID id = clothesRepository.saveAndFlush(new Clothes(userId, "흰 셔츠", ClothesType.TOP, null)).getId();
        entityManager.clear();

        // when
        Clothes found = clothesRepository.findById(id).orElseThrow();

        // then
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getName()).isEqualTo("흰 셔츠");
        assertThat(found.getType()).isEqualTo(ClothesType.TOP);
        assertThat(found.getImageUrl()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 의상의_이름과_종류와_이미지를_변경한다() {
        // given
        Clothes clothes = new Clothes(UUID.randomUUID(), "흰 셔츠", ClothesType.TOP, null);

        // when
        clothes.update("검은 바지", ClothesType.BOTTOM);
        clothes.changeImageUrl("/files/clothes/pants.png");

        // then
        assertThat(clothes.getName()).isEqualTo("검은 바지");
        assertThat(clothes.getType()).isEqualTo(ClothesType.BOTTOM);
        assertThat(clothes.getImageUrl()).isEqualTo("/files/clothes/pants.png");
    }

    @Test
    void 속성_정의의_선택_값은_저장한_순서대로_조회된다() {
        // given
        UUID id = definitionRepository
            .saveAndFlush(new ClothesAttributeDefinition("소재", List.of("면", "울", "나일론")))
            .getId();
        entityManager.clear();

        // when
        ClothesAttributeDefinition found = definitionRepository.findById(id).orElseThrow();

        // then
        assertThat(found.getName()).isEqualTo("소재");
        assertThat(found.getSelectableValues()).containsExactly("면", "울", "나일론");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 속성_정의를_수정하면_선택_값이_교체된다() {
        // given
        UUID id = definitionRepository
            .saveAndFlush(new ClothesAttributeDefinition("소재", List.of("면", "울")))
            .getId();
        entityManager.clear();
        ClothesAttributeDefinition definition = definitionRepository.findById(id).orElseThrow();

        // when
        definition.update("재질", List.of("가죽", "면", "린넨"));
        definitionRepository.saveAndFlush(definition);
        entityManager.clear();

        // then
        ClothesAttributeDefinition found = definitionRepository.findById(id).orElseThrow();
        assertThat(found.getName()).isEqualTo("재질");
        assertThat(found.getSelectableValues()).containsExactly("가죽", "면", "린넨");
        assertThat(definitionRepository.existsByName("재질")).isTrue();
        assertThat(definitionRepository.existsByName("소재")).isFalse();
    }

    @Test
    void 같은_이름의_속성_정의는_저장할_수_없다() {
        // given
        definitionRepository.saveAndFlush(new ClothesAttributeDefinition("소재", List.of("면")));

        // when/then
        assertThatThrownBy(() -> definitionRepository
            .saveAndFlush(new ClothesAttributeDefinition("소재", List.of("울"))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 의상_속성은_의상별로_조회되고_값을_변경할_수_있다() {
        // given
        UUID clothesId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        attributeRepository.saveAndFlush(new ClothesAttribute(clothesId, definitionId, "면"));
        entityManager.clear();

        // when
        List<ClothesAttribute> found = attributeRepository.findAllByClothesId(clothesId);
        found.get(0).changeValue("울");
        attributeRepository.saveAndFlush(found.get(0));
        entityManager.clear();

        // then
        assertThat(attributeRepository.findAllByClothesId(clothesId))
            .singleElement()
            .satisfies(attribute -> {
                assertThat(attribute.getDefinitionId()).isEqualTo(definitionId);
                assertThat(attribute.getValue()).isEqualTo("울");
            });
        assertThat(attributeRepository.findAllByClothesId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void 같은_의상에_같은_속성_정의를_중복_저장할_수_없다() {
        // given
        UUID clothesId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        attributeRepository.saveAndFlush(new ClothesAttribute(clothesId, definitionId, "면"));

        // when/then
        assertThatThrownBy(() -> attributeRepository
            .saveAndFlush(new ClothesAttribute(clothesId, definitionId, "울")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
