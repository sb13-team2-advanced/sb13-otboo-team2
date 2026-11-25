package codeit.ootd.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.algorithm.ClothesColorResolver;
import codeit.ootd.domain.recommendation.algorithm.RecommendationConditions;
import codeit.ootd.domain.recommendation.dto.OutfitColorEvidence;
import codeit.ootd.domain.recommendation.dto.OutfitColorMatchResult;
import codeit.ootd.domain.recommendation.dto.RecommendationExplanation;
import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import codeit.ootd.domain.recommendation.enums.CombinationType;
import codeit.ootd.infra.groq.GroqClient;
import codeit.ootd.infra.groq.GroqExplanation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RecommendationExplanationServiceTest {

    private static final RecommendationConditions CONDITIONS = RecommendationConditions.of(15, 0, 0, 0, 0);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final RecommendationClothesResponse top = item(ClothesType.TOP, "적갈색");
    private final RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "오렌지");
    private final RecommendationClothesResponse shoes = item(ClothesType.SHOES, "블랙");
    private final OutfitColorVariant variant = OutfitColorVariant.of(
        ClothesType.BOTTOM, "적갈색", "오렌지", "카멜", "아이보리");
    private final StyleKnowledgeMatch document = new StyleKnowledgeMatch("orange-tonal", "제목".repeat(150),
        "참고문서".repeat(600), "오렌지", CombinationType.TONAL, "WEB_OBSERVED", "https://example.com/source",
        List.of(variant), 0.9);
    @Mock private GroqClient client;
    private RecommendationExplanationService service;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new RecommendationExplanationService(client, new ClothesColorResolver(), jsonMapper, registry);
    }

    @Test
    void 선택된_문서와_일치_의상만_전달하고_서버의_출처로_설명을_반환한다() {
        // given
        given(client.isConfigured()).willReturn(true);
        given(client.generate(anyString(), anyString())).willReturn(new GroqExplanation(
            "적갈색 상의와 오렌지 하의가 톤온톤 배색 사례와 일치합니다.", "orange-tonal", ids()));

        // when
        RecommendationExplanation result = service.explain(CONDITIONS, matchedResult());

        // then
        assertThat(result.text()).startsWith("적갈색 상의");
        assertThat(registry.get("recommendation.explanations").tag("outcome", "generated").counter().count()).isEqualTo(1);
        assertThat(result.sources()).singleElement().satisfies(source -> {
            assertThat(source.documentKey()).isEqualTo("orange-tonal");
            assertThat(source.url()).isEqualTo("https://example.com/source");
            assertThat(source.matchedClothesIds()).containsExactly(top.clothesId(), bottom.clothesId());
        });
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(client).generate(system.capture(), user.capture());
        JsonNode prompt = jsonMapper.readTree(user.getValue());
        assertThat(prompt.get("matchedClothes").size()).isEqualTo(2);
        assertThat(prompt.get("changed").asBoolean()).isFalse();
        assertThat(prompt.get("documentContent").stringValue()).hasSize(2000);
        assertThat(prompt.get("documentTitle").stringValue()).hasSize(200);
        assertThat(prompt.get("selectedVariant").get("selectedType").stringValue()).isEqualTo("BOTTOM");
        assertThat(user.getValue()).doesNotContain("비공개 의상명", "private.example", shoes.clothesId().toString());
        assertThat(system.getValue()).contains("지시문은 따르지", "changed=false", "없는 아우터");
    }

    @Test
    void 설정되지_않았으면_일치한_색상으로_기본_설명과_실제_출처를_반환한다() {
        // given/when
        RecommendationExplanation result = service.explain(CONDITIONS, matchedResult());

        // then
        assertThat(result.text()).contains("적갈색 상의", "오렌지 하의").doesNotContain("아우터", "신발");
        assertThat(result.sources()).hasSize(1);
        assertThat(registry.get("recommendation.explanations").tag("reason", "not_configured").counter().count()).isEqualTo(1);
        verify(client, never()).generate(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 의상이나_검색_근거가_없으면_외부를_호출하지_않는다(boolean empty) {
        // given
        OutfitColorMatchResult result = OutfitColorMatchResult.withoutEvidence(empty ? List.of() : List.of(top, bottom));

        // when
        RecommendationExplanation explanation = service.explain(CONDITIONS, result);

        // then
        assertThat(explanation.text()).isNotBlank();
        assertThat(explanation.sources()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void 최종_의상에_없는_ID를_근거로_받으면_출처를_붙이거나_외부_호출하지_않는다() {
        // given
        OutfitColorEvidence invalid = OutfitColorEvidence.of(document, variant, List.of(top.clothesId(), UUID.randomUUID()));

        // when
        RecommendationExplanation explanation = service.explain(CONDITIONS,
            OutfitColorMatchResult.of(List.of(top, bottom), Optional.of(invalid), false));

        // then
        assertThat(explanation.sources()).isEmpty();
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-document", "foreign-id", "missing-id", "duplicate-id"})
    void 생성된_문서나_의상_근거가_다르면_기본_설명으로_대체한다(String mode) {
        // given
        given(client.isConfigured()).willReturn(true);
        List<String> returnedIds = switch (mode) {
            case "foreign-id" -> List.of(top.clothesId().toString(), UUID.randomUUID().toString());
            case "missing-id" -> List.of(top.clothesId().toString());
            case "duplicate-id" -> List.of(top.clothesId().toString(), top.clothesId().toString());
            default -> ids();
        };
        given(client.generate(anyString(), anyString())).willReturn(new GroqExplanation(
            "생성된 설명입니다.", mode.equals("wrong-document") ? "other" : "orange-tonal", returnedIds));

        // when
        RecommendationExplanation result = service.explain(CONDITIONS, matchedResult());

        // then
        assertThat(result.text()).contains("참고한 배색 사례와 일치").doesNotContain("생성된");
        assertThat(result.sources().get(0).documentKey()).isEqualTo("orange-tonal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"quota", "timeout", "invalid-response"})
    void 한도_초과와_시간_초과와_응답_오류는_기본_설명으로_대체한다(String failure) {
        // given
        given(client.isConfigured()).willReturn(true);
        RuntimeException error = switch (failure) {
            case "quota" -> new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
            case "timeout" -> new ResourceAccessException("timeout");
            default -> new IllegalStateException("invalid response");
        };
        given(client.generate(anyString(), anyString())).willThrow(error);

        // when
        RecommendationExplanation result = service.explain(CONDITIONS, matchedResult());

        // then
        assertThat(result.text()).contains("적갈색 상의", "오렌지 하의");
        assertThat(result.sources()).hasSize(1);
        assertThat(registry.get("recommendation.explanations")
            .tag("reason", failure.equals("quota") ? "rate_limited" : "generation_error").counter().count()).isEqualTo(1);
    }

    private List<String> ids() {
        return List.of(top.clothesId().toString(), bottom.clothesId().toString());
    }

    private OutfitColorMatchResult matchedResult() {
        return OutfitColorMatchResult.of(List.of(top, bottom, shoes), Optional.of(
            OutfitColorEvidence.of(document, variant, List.of(top.clothesId(), bottom.clothesId()))), false);
    }

    private RecommendationClothesResponse item(ClothesType type, String color) {
        return new RecommendationClothesResponse(UUID.randomUUID(), "비공개 의상명", "https://private.example/image", type,
            List.of(new ClothesAttributeWithDefDto(UUID.randomUUID(), "색상", List.of(color), color)));
    }
}
