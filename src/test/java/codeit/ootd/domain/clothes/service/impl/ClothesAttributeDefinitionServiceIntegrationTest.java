package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDto;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.service.ClothesAttributeDefinitionService;
import codeit.ootd.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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

    @Test
    void 이름_오름차순과_내림차순으로_조회한다() {
        createFive();

        assertThat(service.list("name", SortDirection.ASCENDING, null)).extracting(ClothesAttributeDefDto::name)
                .containsExactly("color", "material", "pattern", "season", "thickness");
        assertThat(service.list("name", SortDirection.DESCENDING, null)).extracting(ClothesAttributeDefDto::name)
                .containsExactly("thickness", "season", "pattern", "material", "color");
    }

    @Test
    void 조회한_속성_정의의_선택_가능한_값이_순서대로_채워진다() {
        createFive();

        assertThat(service.list("name", SortDirection.ASCENDING, null)).allSatisfy(definition ->
                assertThat(definition.selectableValues()).containsExactly("a", "b"));
    }

    @Test
    void 검색어는_대소문자를_구분하지_않고_이름에_포함된_항목만_찾는다() {
        createFive();

        assertThat(service.list("name", SortDirection.ASCENDING, "OL")).extracting(ClothesAttributeDefDto::name)
                .containsExactly("color");
    }

    @Test
    void 검색어의_와일드카드_문자는_문자_그대로_취급한다() {
        createFive();

        assertThat(service.list("name", SortDirection.ASCENDING, "%")).isEmpty();
        assertThat(service.list("name", SortDirection.ASCENDING, "_")).isEmpty();
    }

    @Test
    void 생성일이_모두_같아도_id를_보조_기준으로_순서가_고정되고_오름차순과_내림차순이_정확히_뒤집힌다() {
        createFive();
        entityManager.createNativeQuery("update clothes_attribute_definition set created_at = ?1")
                .setParameter(1, Instant.parse("2026-10-27T01:00:00Z"))
                .executeUpdate();
        entityManager.clear();

        List<UUID> ascending = service.list("createdAt", SortDirection.ASCENDING, null).stream()
                .map(ClothesAttributeDefDto::id).toList();
        List<UUID> descending = service.list("createdAt", SortDirection.DESCENDING, null).stream()
                .map(ClothesAttributeDefDto::id).toList();

        assertThat(ascending).hasSize(5).doesNotHaveDuplicates();
        List<UUID> reversed = new ArrayList<>(ascending);
        Collections.reverse(reversed);
        assertThat(descending).containsExactlyElementsOf(reversed);
    }

    private void createFive() {
        for (String name : List.of("thickness", "color", "season", "material", "pattern")) {
            service.create(new ClothesAttributeDefCreateRequest(name, List.of("a", "b")));
        }
        entityManager.flush();
        entityManager.clear();
    }

    private long countSelectableValues(UUID definitionId) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from selectable_attribute where definition_id = ?1")
                .setParameter(1, definitionId)
                .getSingleResult()).longValue();
    }
}
