package codeit.ootd.domain.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.recommendation.algorithm.RecommendationConditions;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import codeit.ootd.domain.recommendation.enums.CombinationType;
import codeit.ootd.domain.recommendation.service.StyleKnowledgeSearchService;
import codeit.ootd.domain.recommendation.service.OutfitRecommendationService;
import codeit.ootd.infra.embedding.EmbeddingClient;
import codeit.ootd.infra.embedding.EmbeddingResponse;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private OutfitRecommendationService outfitRecommendationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Autowired
    @Qualifier("embeddingVectorCache")
    private Cache<?, ?> embeddingVectorCache;

    @BeforeEach
    void 캐시를_비워_독립적인_추천_조건을_준비한다() {
        embeddingVectorCache.invalidateAll();
    }

    @Test
    void 임베딩을_재사용해도_변경된_DB_문서를_다시_조회한다() {
        // given
        insert("cache-document", "오렌지", "TONAL", "APPROVED", MODEL, vector(1, 0));
        given(embeddingClient.embed("오렌지 캐시 검증")).willReturn(QUERY);
        var before = searchService.search("오렌지 캐시 검증", "오렌지", null);

        // when
        jdbcTemplate.update("UPDATE style_knowledge SET title = '변경된 문서' WHERE document_key = 'cache-document'");
        var after = searchService.search("오렌지 캐시 검증", "오렌지", null);

        // then
        assertThat(before.get(0).title()).isEqualTo("배색 사례");
        assertThat(after.get(0).title()).isEqualTo("변경된 문서");
        verify(embeddingClient, times(1)).embed("오렌지 캐시 검증");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 실제_벡터_검색_결과가_의상_선택에_반영되고_임베딩은_트랜잭션_밖에서_호출된다() {
        // given
        insert("outfit-integration", "오렌지", "TONAL", "APPROVED", MODEL, vector(1, 0));
        RecommendationClothesResponse blueTop = coloredItem(ClothesType.TOP, "블루");
        RecommendationClothesResponse brownTop = coloredItem(ClothesType.TOP, "적갈색");
        RecommendationClothesResponse bottom = coloredItem(ClothesType.BOTTOM, "주황색");
        given(embeddingClient.embed(anyString())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return QUERY;
        });

        try {
            // when
            List<RecommendationClothesResponse> result = outfitRecommendationService.recommend(
                RecommendationConditions.of(25, 0, 0, 0, 0), List.of(blueTop, brownTop, bottom));

            // then
            assertThat(result).containsExactly(brownTop, bottom);
            verify(embeddingClient).embed("오렌지 하의를 기준으로 상의, 하의, 아우터와 어울리는 색 조합");
        } finally {
            jdbcTemplate.update("DELETE FROM style_knowledge WHERE document_key = 'outfit-integration'");
        }
    }

    @Test
    void 상위_트랜잭션도_임베딩_호출_동안_중지하고_실패_뒤에는_복원한다() {
        // given
        RecommendationClothesResponse top = coloredItem(ClothesType.TOP, "블루");
        RecommendationClothesResponse bottom = coloredItem(ClothesType.BOTTOM, "주황색");
        given(embeddingClient.embed(anyString())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new IllegalStateException("유효하지 않은 임베딩");
        });

        // when
        List<RecommendationClothesResponse> result = outfitRecommendationService.recommend(
            RecommendationConditions.of(25, 0, 0, 0, 0), List.of(top, bottom));

        // then
        assertThat(result).containsExactly(top, bottom);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 지식_조회_SQL이_실패해도_기존_추천을_반환하고_상위_트랜잭션은_사용할_수_있다() {
        // given
        insert("database-failure-integration", "오렌지", "TONAL", "APPROVED", MODEL, vector(1, 0));
        RecommendationClothesResponse top = coloredItem(ClothesType.TOP, "블루");
        RecommendationClothesResponse bottom = coloredItem(ClothesType.BOTTOM, "주황색");
        given(embeddingClient.embed(anyString())).willReturn(
            new EmbeddingResponse(MODEL, 2, List.of(1.0, 0.0)));

        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // when
                List<RecommendationClothesResponse> result = outfitRecommendationService.recommend(
                    RecommendationConditions.of(25, 0, 0, 0, 0), List.of(top, bottom));

                // then
                assertThat(result).containsExactly(top, bottom);
                assertThat(status.isRollbackOnly()).isFalse();
                assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
            });
        } finally {
            jdbcTemplate.update("DELETE FROM style_knowledge WHERE document_key = 'database-failure-integration'");
        }
    }

    private RecommendationClothesResponse coloredItem(ClothesType type, String color) {
        return new RecommendationClothesResponse(UUID.randomUUID(), color, null, type,
            List.of(new ClothesAttributeWithDefDto(UUID.randomUUID(), "색상", List.of(color), color)));
    }

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
