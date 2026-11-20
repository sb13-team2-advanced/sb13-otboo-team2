package codeit.ootd.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.algorithm.ClothesColorResolver;
import codeit.ootd.domain.recommendation.algorithm.OutfitColorMatcher;
import codeit.ootd.domain.recommendation.algorithm.OutfitRecommender;
import codeit.ootd.domain.recommendation.algorithm.RecommendationConditions;
import codeit.ootd.domain.recommendation.dto.OutfitColorMatchResult;
import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import codeit.ootd.domain.recommendation.enums.CombinationType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class OutfitRecommendationServiceTest {

    private final RecommendationConditions conditions = RecommendationConditions.of(15, 0, 0, 0, 0);
    private final ClothesColorResolver colorResolver = new ClothesColorResolver();
    private final RecommendationClothesResponse top = item(ClothesType.TOP, "블루");
    private final RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "주황색");

    @Mock private OutfitRecommender weatherRecommender;
    @Mock private StyleKnowledgeSearchService searchService;
    @Mock private OutfitColorMatcher matcher;
    private OutfitRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new OutfitRecommendationService(weatherRecommender, colorResolver, searchService, matcher);
    }

    @Test
    void 하의의_정규화한_색상으로_한_번_검색하고_결과를_선택에_반영한다() {
        // given
        List<RecommendationClothesResponse> clothes = List.of(top, bottom);
        List<StyleKnowledgeMatch> documents = List.of(new StyleKnowledgeMatch(
            "orange", "배색", "내용", "오렌지", CombinationType.TONAL, "WEB_OBSERVED", null,
            List.of(OutfitColorVariant.of(ClothesType.BOTTOM, "적갈색", "오렌지", "카멜", "아이보리")), 0.9));
        given(weatherRecommender.recommend(conditions, clothes)).willReturn(clothes);
        given(searchService.search(contains("오렌지 하의"), eq("오렌지"), isNull())).willReturn(documents);
        given(matcher.match(conditions, clothes, clothes, bottom, documents))
            .willReturn(OutfitColorMatchResult.withoutEvidence(clothes));

        // when
        List<RecommendationClothesResponse> result = service.recommend(conditions, clothes);

        // then
        assertThat(result).isSameAs(clothes);
        verify(searchService).search(contains("오렌지 하의"), eq("오렌지"), isNull());
        verify(matcher).match(conditions, clothes, clothes, bottom, documents);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOP", "OUTER"})
    void 하의_색상을_모르면_알고_있는_상의나_아우터를_기준으로_검색한다(String type) {
        // given
        RecommendationClothesResponse unknownTop = item(ClothesType.TOP, "미등록");
        RecommendationClothesResponse unknownBottom = item(ClothesType.BOTTOM, "미등록");
        RecommendationClothesResponse anchor = item(ClothesType.valueOf(type), "검정");
        List<RecommendationClothesResponse> clothes = type.equals("TOP")
            ? List.of(anchor, unknownBottom) : List.of(unknownTop, unknownBottom, anchor);
        given(weatherRecommender.recommend(conditions, clothes)).willReturn(clothes);
        given(matcher.match(conditions, clothes, clothes, anchor, List.of()))
            .willReturn(OutfitColorMatchResult.withoutEvidence(clothes));

        // when
        service.recommend(conditions, clothes);

        // then
        verify(searchService).search(contains(type.equals("TOP") ? "블랙 상의" : "블랙 아우터"),
            eq("블랙"), isNull());
        verify(matcher).match(conditions, clothes, clothes, anchor, List.of());
    }

    @Test
    void 완성할_코디가_없으면_검색하지_않는다() {
        // given
        given(weatherRecommender.recommend(conditions, List.of(top))).willReturn(List.of());

        // when/then
        assertThat(service.recommend(conditions, List.of(top))).isEmpty();
        verifyNoInteractions(searchService, matcher);
    }

    @Test
    void 기준으로_삼을_색상이_없으면_기존_추천을_유지한다() {
        // given
        List<RecommendationClothesResponse> clothes = List.of(
            item(ClothesType.TOP, "미등록"), item(ClothesType.BOTTOM, "미등록"));
        given(weatherRecommender.recommend(conditions, clothes)).willReturn(clothes);

        // when/then
        assertThat(service.recommend(conditions, clothes)).isSameAs(clothes);
        verifyNoInteractions(searchService, matcher);
    }

    @Test
    void 검색된_문서가_없으면_실제_매칭_단계도_기존_추천을_그대로_반환한다() {
        // given
        List<RecommendationClothesResponse> clothes = List.of(top, bottom);
        given(weatherRecommender.recommend(conditions, clothes)).willReturn(clothes);
        OutfitRecommendationService realMatcherService = new OutfitRecommendationService(
            weatherRecommender, colorResolver, searchService,
            new OutfitColorMatcher(weatherRecommender, colorResolver));

        // when/then
        assertThat(realMatcherService.recommend(conditions, clothes)).isSameAs(clothes);
        verify(searchService).search(contains("오렌지 하의"), eq("오렌지"), isNull());
    }

    @Test
    void 원피스는_상의_하의_배색_문서로_변경하지_않는다() {
        // given
        List<RecommendationClothesResponse> clothes = List.of(
            item(ClothesType.DRESS, "블랙"), item(ClothesType.OUTER, "베이지"));
        given(weatherRecommender.recommend(conditions, clothes)).willReturn(clothes);

        // when/then
        assertThat(service.recommend(conditions, clothes)).isSameAs(clothes);
        verifyNoInteractions(searchService, matcher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "embedding", "database"})
    void 검색_통신이나_임베딩_응답이나_DB가_실패하면_기존_추천을_반환한다(String failure) {
        // given
        List<RecommendationClothesResponse> clothes = List.of(top, bottom);
        RuntimeException exception = switch (failure) {
            case "http" -> new ResourceAccessException("연결 실패");
            case "embedding" -> new IllegalStateException("잘못된 임베딩");
            default -> new DataAccessResourceFailureException("DB 조회 실패");
        };
        given(weatherRecommender.recommend(conditions, clothes)).willReturn(clothes);
        given(searchService.search(anyString(), anyString(), isNull())).willThrow(exception);

        // when/then
        assertThat(service.recommend(conditions, clothes)).isSameAs(clothes);
        verify(matcher, never()).match(any(), any(), any(), any(), any());
    }

    private RecommendationClothesResponse item(ClothesType type, String color) {
        return new RecommendationClothesResponse(UUID.randomUUID(), color, null, type,
            List.of(new ClothesAttributeWithDefDto(UUID.randomUUID(), "색상", List.of(color), color)));
    }
}
