package codeit.ootd.domain.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import codeit.ootd.domain.recommendation.enums.CombinationType;
import codeit.ootd.domain.recommendation.service.StyleKnowledgeSearchService;
import codeit.ootd.infra.embedding.EmbeddingClient;
import codeit.ootd.infra.embedding.EmbeddingResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "ootd.recommendation.knowledge.include-drafts=false"
})
@ActiveProfiles("test")
@Transactional
class StyleKnowledgeRepositoryIntegrationTest {

    private static final String MODEL = "intfloat/multilingual-e5-small";
    private static final int DIMENSIONS = 384;
    private static final EmbeddingResponse QUERY = new EmbeddingResponse(MODEL, DIMENSIONS, vector(1, 0));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:0.8.7-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private StyleKnowledgeRepository repository;
    @Autowired
    private StyleKnowledgeSearchService searchService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Test
    void 검색_서비스는_임베딩_응답으로_기준색의_문서를_거리순으로_조회한다() {
        // given
        insert("orange-tonal", "오렌지", "TONAL", "APPROVED", MODEL, vector(1, 0));
        insert("orange-basic", "오렌지", "BASIC", "APPROVED", MODEL, vector(0.8, 0.6));
        insert("pink-tonal", "핑크", "TONAL", "APPROVED", MODEL, vector(1, 0));
        given(embeddingClient.embed("오렌지 코디")).willReturn(QUERY);

        // when
        List<StyleKnowledgeMatch> result = searchService.search("오렌지 코디", "오렌지", null);

        // then
        assertThat(result).extracting(StyleKnowledgeMatch::documentKey)
            .containsExactly("orange-tonal", "orange-basic");
        assertThat(result.get(0).similarity()).isCloseTo(1.0, within(0.000001));
        assertThat(result.get(1).similarity()).isCloseTo(0.8, within(0.000001));
        assertThat(result.get(0).combinationType()).isEqualTo(CombinationType.TONAL);
        assertThat(result.get(0).baseColor()).isEqualTo("오렌지");
        assertThat(result.get(0).title()).isEqualTo("배색 사례");
        assertThat(result.get(0).content()).isEqualTo("상의와 하의의 색 배치");
        assertThat(result.get(0).sourceType()).isEqualTo("WEB_OBSERVED");
        assertThat(result.get(0).sourceUrl()).isEqualTo("https://example.com/guide");
        assertThat(result.get(0).outfitVariants()).containsExactly(
            OutfitColorVariant.of(ClothesType.BOTTOM, "적갈색", "오렌지", "카멜", "아이보리"));
        verify(embeddingClient).embed("오렌지 코디");
    }

    @Test
    void 색_배치가_잘못된_문서도_검색은_유지하고_빈_색_배치를_반환한다() {
        // given
        insert("invalid-metadata", "오렌지", "TONAL", "APPROVED", MODEL, vector(1, 0));
        insert("valid-metadata", "오렌지", "TONAL", "APPROVED", MODEL, vector(0.8, 0.6));
        jdbcTemplate.update("""
            UPDATE style_knowledge
            SET metadata = '{"outfitVariants":[{"selectedType":"BOTTOM","topColor":"적갈색"}]}'::jsonb
            WHERE document_key = 'invalid-metadata'
            """);

        // when
        List<StyleKnowledgeMatch> result = repository.search(QUERY, "오렌지", null, false, 3);

        // then
        assertThat(result).extracting(StyleKnowledgeMatch::documentKey)
            .containsExactly("invalid-metadata", "valid-metadata");
        assertThat(result.get(0).outfitVariants()).isEmpty();
        assertThat(result.get(1).outfitVariants()).containsExactly(
            OutfitColorVariant.of(ClothesType.BOTTOM, "적갈색", "오렌지", "카멜", "아이보리"));
    }

    @Test
    void 기본_검색은_다른_모델과_미승인_문서와_임베딩_없는_문서를_제외한다() {
        // given
        insert("approved", "오렌지", "TONAL", "APPROVED", MODEL, vector(0.8, 0.6));
        insert("draft", "오렌지", "TONAL", "DRAFT", MODEL, vector(1, 0));
        insert("rejected", "오렌지", "TONAL", "REJECTED", MODEL, vector(1, 0));
        insert("other-model", "오렌지", "TONAL", "APPROVED", "other-model", vector(1, 0));
        insert("no-vector", "오렌지", "TONAL", "APPROVED", null, null);

        // when
        List<StyleKnowledgeMatch> result = repository.search(QUERY, "오렌지", CombinationType.TONAL, false, 3);

        // then
        assertThat(result).extracting(StyleKnowledgeMatch::documentKey).containsExactly("approved");
    }

    @Test
    void 초안을_포함해도_거절된_문서는_검색하지_않는다() {
        // given
        insert("approved", "오렌지", "TONAL", "APPROVED", MODEL, vector(0.8, 0.6));
        insert("draft", "오렌지", "TONAL", "DRAFT", MODEL, vector(1, 0));
        insert("rejected", "오렌지", "TONAL", "REJECTED", MODEL, vector(1, 0));

        // when
        List<StyleKnowledgeMatch> result = repository.search(QUERY, "오렌지", null, true, 3);

        // then
        assertThat(result).extracting(StyleKnowledgeMatch::documentKey).containsExactly("draft", "approved");
    }

    @Test
    void 지정한_배색_유형만_반환한다() {
        // given
        insert("tonal", "오렌지", "TONAL", "APPROVED", MODEL, vector(0.8, 0.6));
        insert("basic", "오렌지", "BASIC", "APPROVED", MODEL, vector(1, 0));

        // when
        List<StyleKnowledgeMatch> result = repository.search(QUERY, "오렌지", CombinationType.TONAL, false, 3);

        // then
        assertThat(result).extracting(StyleKnowledgeMatch::documentKey).containsExactly("tonal");
    }

    @Test
    void 거리가_같으면_문서_키로_정렬하고_검색_개수를_제한한다() {
        // given
        insert("orange-b", "오렌지", "BASIC", "APPROVED", MODEL, vector(1, 0));
        insert("orange-a", "오렌지", "TONAL", "APPROVED", MODEL, vector(1, 0));

        // when
        List<StyleKnowledgeMatch> result = repository.search(QUERY, "오렌지", null, false, 1);

        // then
        assertThat(result).extracting(StyleKnowledgeMatch::documentKey).containsExactly("orange-a");
    }

    @Test
    void 기준색은_SQL_구문이_아닌_매개변수로_전달한다() {
        // given
        insert("orange", "오렌지", "BASIC", "APPROVED", MODEL, vector(1, 0));

        // when
        List<StyleKnowledgeMatch> result = repository.search(QUERY, "오렌지' OR 1=1 --", null, false, 3);

        // then
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 검색_개수가_양수가_아니면_실패한다(int limit) {
        // given/when/then
        assertThatThrownBy(() -> repository.search(QUERY, "오렌지", null, false, limit))
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasMessage("검색 개수는 1 이상이어야 합니다.");
    }

    private void insert(String key, String color, String type, String status, String model, List<Double> vector) {
        jdbcTemplate.update("""
            INSERT INTO style_knowledge (
                document_key, title, content, base_color, combination_type,
                source_type, source_url, review_status, metadata, embedding_model, embedding
            ) VALUES (?, '배색 사례', '상의와 하의의 색 배치', ?, ?, 'WEB_OBSERVED',
                      'https://example.com/guide', ?,
                      '{"outfitVariants":[{"selectedType":"BOTTOM","topColor":"적갈색",
                        "bottomColor":"오렌지","outerColor":"카멜","accessoryColor":"아이보리"}]}'::jsonb,
                      ?, CAST(? AS vector))
            """, key, color, type, status, model, vector == null ? null : vector.toString());
    }

    private static List<Double> vector(double first, double second) {
        List<Double> vector = new ArrayList<>(Collections.nCopies(DIMENSIONS, 0.0));
        vector.set(0, first);
        vector.set(1, second);
        return vector;
    }
}
