package codeit.ootd.domain.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesAttributeDefSortBy;
import codeit.ootd.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 속성 정의 목록의 커서 조회가 정렬 기준·방향·검색어별로 빠짐없이, 중복 없이 이어지는지 검증한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ClothesAttributeDefinitionRepositoryTest {

    @Autowired
    private ClothesAttributeDefinitionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        for (String name : List.of("thickness", "color", "season", "material", "pattern")) {
            repository.save(new ClothesAttributeDefinition(name, List.of("a", "b", "c")));
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 이름_오름차순으로_커서를_따라가면_모든_항목을_한_번씩_정렬된_순서로_조회한다() {
        List<ClothesAttributeDefinition> all = pageThrough(
                ClothesAttributeDefSortBy.NAME, SortDirection.ASCENDING, null, 2);

        assertThat(all).extracting(ClothesAttributeDefinition::getName)
                .containsExactly("color", "material", "pattern", "season", "thickness");
    }

    @Test
    void 이름_내림차순으로_조회한다() {
        List<ClothesAttributeDefinition> all = pageThrough(
                ClothesAttributeDefSortBy.NAME, SortDirection.DESCENDING, null, 2);

        assertThat(all).extracting(ClothesAttributeDefinition::getName)
                .containsExactly("thickness", "season", "pattern", "material", "color");
    }

    @Test
    void 생성일이_모두_같아도_id를_보조_기준으로_빠짐없이_중복없이_조회한다() {
        entityManager.createNativeQuery("update clothes_attribute_definition set created_at = ?1")
                .setParameter(1, Instant.parse("2026-10-27T01:00:00Z"))
                .executeUpdate();
        entityManager.clear();

        List<ClothesAttributeDefinition> ascending = pageThrough(
                ClothesAttributeDefSortBy.CREATED_AT, SortDirection.ASCENDING, null, 2);
        List<ClothesAttributeDefinition> descending = pageThrough(
                ClothesAttributeDefSortBy.CREATED_AT, SortDirection.DESCENDING, null, 2);

        assertThat(ascending).extracting(ClothesAttributeDefinition::getId).doesNotHaveDuplicates().hasSize(5);
        assertThat(descending).extracting(ClothesAttributeDefinition::getId).doesNotHaveDuplicates().hasSize(5);
    }

    @Test
    void 검색어는_대소문자를_구분하지_않고_이름에_포함된_항목만_찾는다() {
        List<ClothesAttributeDefinition> found = pageThrough(
                ClothesAttributeDefSortBy.NAME, SortDirection.ASCENDING, "OL", 10);

        assertThat(found).extracting(ClothesAttributeDefinition::getName).containsExactly("color");
        assertThat(repository.countByKeyword("OL")).isEqualTo(1L);
    }

    @Test
    void 검색어의_와일드카드_문자는_문자_그대로_취급한다() {
        assertThat(repository.countByKeyword("%")).isZero();
        assertThat(repository.countByKeyword("_")).isZero();
    }

    @Test
    void 전체_개수는_커서와_무관하게_검색어_기준으로_센다() {
        assertThat(repository.countByKeyword(null)).isEqualTo(5L);
    }

    @Test
    void 조회한_속성_정의의_선택_가능한_값이_순서대로_채워진다() {
        List<ClothesAttributeDefinition> page = repository.findPage(
                null, ClothesAttributeDefSortBy.NAME, SortDirection.ASCENDING, null, null, 5);

        assertThat(page).hasSize(5);
        assertThat(page).allSatisfy(definition ->
                assertThat(definition.getSelectableValues()).containsExactly("a", "b", "c"));
    }

    private List<ClothesAttributeDefinition> pageThrough(
            ClothesAttributeDefSortBy sortBy, SortDirection direction, String keyword, int size) {
        List<ClothesAttributeDefinition> all = new ArrayList<>();
        Object cursorValue = null;
        UUID idAfter = null;

        for (int guard = 0; guard < 20; guard++) {
            List<ClothesAttributeDefinition> page =
                    repository.findPage(keyword, sortBy, direction, cursorValue, idAfter, size);
            all.addAll(page);
            if (page.size() < size) {
                return all;
            }
            ClothesAttributeDefinition last = page.get(page.size() - 1);
            cursorValue = sortBy.parseCursor(sortBy.cursorOf(last));
            idAfter = last.getId();
        }
        throw new IllegalStateException("커서 조회가 끝나지 않습니다.");
    }
}
