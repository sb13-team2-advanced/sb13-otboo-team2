package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDto;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.service.ClothesAttributeDefinitionService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스를 통한 등록·수정·삭제가 선택 가능한 값과 의상 속성 값까지 일관되게 반영되는지 검증한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ClothesAttributeDefinitionServiceIntegrationTest {

    @Autowired
    private ClothesAttributeDefinitionService service;

    @Autowired
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Autowired
    private ClothesAttributeRepository attributeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 등록한_속성_정의는_id와_생성일이_채워지고_값_순서가_유지된다() {
        ClothesAttributeDefDto created = service.create(
                new ClothesAttributeDefCreateRequest("두께", List.of("얇음", "보통", "두꺼움")));
        entityManager.clear();

        ClothesAttributeDefDto found = ClothesAttributeDefDto.from(
                definitionRepository.findById(created.id()).orElseThrow());

        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(found.selectableValues()).containsExactly("얇음", "보통", "두꺼움");
    }

    @Test
    void 수정하면_이름과_값이_바뀌고_값은_새_순서로_저장된다() {
        ClothesAttributeDefDto created = service.create(
                new ClothesAttributeDefCreateRequest("두께", List.of("얇음", "보통", "두꺼움")));
        entityManager.flush();
        entityManager.clear();

        service.update(created.id(), new ClothesAttributeDefUpdateRequest("소재 두께", List.of("두꺼움", "얇음")));
        entityManager.flush();
        entityManager.clear();

        ClothesAttributeDefDto found = ClothesAttributeDefDto.from(
                definitionRepository.findById(created.id()).orElseThrow());
        assertThat(found.name()).isEqualTo("소재 두께");
        assertThat(found.selectableValues()).containsExactly("두꺼움", "얇음");
    }

    @Test
    void 삭제하면_선택_가능한_값과_의상에_붙은_속성_값도_함께_지워지고_다른_정의는_남는다() {
        ClothesAttributeDefDto target = service.create(
                new ClothesAttributeDefCreateRequest("두께", List.of("얇음", "두꺼움")));
        ClothesAttributeDefDto other = service.create(
                new ClothesAttributeDefCreateRequest("소재", List.of("면", "울")));
        attributeRepository.save(new ClothesAttribute(UUID.randomUUID(), target.id(), "얇음"));
        attributeRepository.save(new ClothesAttribute(UUID.randomUUID(), target.id(), "두꺼움"));
        attributeRepository.save(new ClothesAttribute(UUID.randomUUID(), other.id(), "면"));
        entityManager.flush();
        entityManager.clear();

        service.delete(target.id());
        entityManager.flush();
        entityManager.clear();

        assertThat(definitionRepository.findById(target.id())).isEmpty();
        assertThat(countSelectableValues(target.id())).isZero();
        assertThat(attributeRepository.findAll()).extracting(ClothesAttribute::getDefinitionId)
                .doesNotContain(target.id())
                .contains(other.id());
        assertThat(definitionRepository.findById(other.id())).isPresent();
        assertThat(countSelectableValues(other.id())).isEqualTo(2L);
    }

    private long countSelectableValues(UUID definitionId) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from selectable_attribute where definition_id = ?1")
                .setParameter(1, definitionId)
                .getSingleResult()).longValue();
    }
}
