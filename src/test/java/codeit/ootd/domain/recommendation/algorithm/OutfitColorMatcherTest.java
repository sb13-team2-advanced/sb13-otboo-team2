package codeit.ootd.domain.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import codeit.ootd.domain.recommendation.enums.CombinationType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutfitColorMatcherTest {

    private final OutfitColorMatcher matcher = new OutfitColorMatcher(
        new OutfitRecommender(), new ClothesColorResolver());
    private final RecommendationConditions calm = RecommendationConditions.of(15, 0, 0, 0, 0);
    private final RecommendationClothesResponse blueTop = item(ClothesType.TOP, "블루", "보통");
    private final RecommendationClothesResponse orangeBottom = item(ClothesType.BOTTOM, "주황색", "보통");
    private final RecommendationClothesResponse brownTop = item(ClothesType.TOP, "적갈색", "보통");
    private final RecommendationClothesResponse camelOuter = item(ClothesType.OUTER, "카멜", "보통");
    private final OutfitColorVariant tonal = OutfitColorVariant.of(
        ClothesType.BOTTOM, "적갈색", "오렌지", "카멜", "아이보리");

    @Test
    void 같은_날씨_적합도의_상의와_아우터를_한_색_배치에_맞춰_교체한다() {
        // given
        RecommendationClothesResponse blackOuter = item(ClothesType.OUTER, "검정", "보통");
        List<RecommendationClothesResponse> baseline = List.of(blueTop, orangeBottom, blackOuter);

        // when
        List<RecommendationClothesResponse> result = matcher.match(calm,
            List.of(blueTop, brownTop, orangeBottom, blackOuter, camelOuter), baseline,
            orangeBottom, List.of(document("오렌지", tonal)));

        // then
        assertThat(result).containsExactly(brownTop, orangeBottom, camelOuter);
    }

    @Test
    void 색상이_맞아도_날씨에_더_부적합한_두께로_교체하지_않는다() {
        // given
        RecommendationClothesResponse thickTop = item(ClothesType.TOP, "적갈색", "두꺼움");
        List<RecommendationClothesResponse> baseline = List.of(blueTop, orangeBottom);

        // when
        List<RecommendationClothesResponse> result = matcher.match(calm,
            List.of(blueTop, thickTop, orangeBottom), baseline,
            orangeBottom, List.of(document("오렌지", tonal)));

        // then
        assertThat(result).isSameAs(baseline);
    }

    @ParameterizedTest
    @CsvSource({"방수, 0.8, 0", "방풍, 0, 4"})
    void 색상이_맞아도_필요한_방수나_방풍_성능을_낮추지_않는다(
        String attribute, double probability, double wind) {
        // given
        RecommendationClothesResponse protectedTop = protectedTop("블루", attribute, "높음");
        RecommendationClothesResponse matchingTop = protectedTop("적갈색", attribute, "낮음");
        RecommendationConditions conditions = RecommendationConditions.of(18, probability, 0, 0, wind);
        List<RecommendationClothesResponse> baseline = List.of(protectedTop, orangeBottom);

        // when
        List<RecommendationClothesResponse> result = matcher.match(conditions,
            List.of(protectedTop, matchingTop, orangeBottom), baseline,
            orangeBottom, List.of(document("오렌지", tonal)));

        // then
        assertThat(result).isSameAs(baseline);
    }

    @Test
    void 더운_날_기존_코디에_없던_아우터를_배색_때문에_추가하지_않는다() {
        // given
        RecommendationConditions hot = RecommendationConditions.of(25, 0, 0, 0, 0);
        RecommendationClothesResponse top = item(ClothesType.TOP, "블루", "얇음");
        RecommendationClothesResponse matchingTop = item(ClothesType.TOP, "적갈색", "얇음");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "오렌지", "얇음");

        // when
        List<RecommendationClothesResponse> result = matcher.match(hot,
            List.of(top, matchingTop, bottom, camelOuter), List.of(top, bottom),
            bottom, List.of(document("오렌지", tonal)));

        // then
        assertThat(result).containsExactly(matchingTop, bottom);
    }

    @Test
    void 따뜻한_비오는_날의_아우터도_기존과_동일하게_얇은_두께에서_선택한다() {
        // given
        RecommendationConditions rain = RecommendationConditions.of(21, 0.8, 0, 0, 0);
        RecommendationClothesResponse outer = item(ClothesType.OUTER, "블랙", "얇음");
        RecommendationClothesResponse matchingOuter = item(ClothesType.OUTER, "카멜", "얇음");

        // when
        List<RecommendationClothesResponse> result = matcher.match(rain,
            List.of(brownTop, orangeBottom, outer, camelOuter, matchingOuter),
            List.of(brownTop, orangeBottom, outer), orangeBottom, List.of(document("오렌지", tonal)));

        // then
        assertThat(result).containsExactly(brownTop, orangeBottom, matchingOuter);
    }

    @Test
    void 여러_문서의_색을_섞지_않고_일치_개수가_가장_큰_한_색_배치를_선택한다() {
        // given
        RecommendationClothesResponse whiteTop = item(ClothesType.TOP, "화이트", "보통");
        RecommendationClothesResponse blackOuter = item(ClothesType.OUTER, "블랙", "보통");
        OutfitColorVariant partial = OutfitColorVariant.of(
            ClothesType.BOTTOM, "화이트", "오렌지", "틸", "카멜");

        // when
        List<RecommendationClothesResponse> result = matcher.match(calm,
            List.of(blueTop, whiteTop, brownTop, orangeBottom, blackOuter, camelOuter),
            List.of(blueTop, orangeBottom, blackOuter), orangeBottom,
            List.of(document("오렌지", partial), document("오렌지", tonal)));

        // then
        assertThat(result).containsExactly(brownTop, orangeBottom, camelOuter);
    }

    @Test
    void 기존_코디가_다른_배색에_이미_동일하게_잘_맞으면_유지한다() {
        // given
        OutfitColorVariant basic = OutfitColorVariant.of(
            ClothesType.BOTTOM, "블루", "오렌지", "블랙", "카멜");
        List<RecommendationClothesResponse> baseline = List.of(blueTop, orangeBottom);

        // when
        List<RecommendationClothesResponse> result = matcher.match(calm,
            List.of(blueTop, brownTop, orangeBottom), baseline, orangeBottom,
            List.of(document("오렌지", tonal), document("오렌지", basic)));

        // then
        assertThat(result).isSameAs(baseline);
    }

    @Test
    void 개선_점수가_같으면_검색_순서가_앞선_배색을_선택한다() {
        // given
        RecommendationClothesResponse whiteTop = item(ClothesType.TOP, "화이트", "보통");
        OutfitColorVariant basic = OutfitColorVariant.of(
            ClothesType.BOTTOM, "화이트", "오렌지", "블랙", "카멜");

        // when
        List<RecommendationClothesResponse> result = matcher.match(calm,
            List.of(blueTop, brownTop, whiteTop, orangeBottom), List.of(blueTop, orangeBottom),
            orangeBottom, List.of(document("오렌지", tonal), document("오렌지", basic)));

        // then
        assertThat(result).containsExactly(brownTop, orangeBottom);
    }

    @Test
    void 신발은_종류가_명시되지_않은_소품_색상으로_교체하지_않는다() {
        // given
        RecommendationClothesResponse shoes = item(ClothesType.SHOES, "블랙", "보통");
        RecommendationClothesResponse matchingShoes = item(ClothesType.SHOES, "아이보리", "보통");

        // when
        List<RecommendationClothesResponse> result = matcher.match(calm,
            List.of(blueTop, brownTop, orangeBottom, shoes, matchingShoes),
            List.of(blueTop, orangeBottom, shoes), orangeBottom, List.of(document("오렌지", tonal)));

        // then
        assertThat(result).containsExactly(brownTop, orangeBottom, shoes);
    }

    @Test
    void 색상_없는_기존_의상도_같은_날씨_적합도의_알려진_색상으로_교체할_수_있다() {
        // given
        RecommendationClothesResponse unknown = new RecommendationClothesResponse(
            UUID.randomUUID(), "색상 없음", null, ClothesType.TOP, List.of());

        // when
        List<RecommendationClothesResponse> result = matcher.match(calm,
            List.of(unknown, brownTop, orangeBottom), List.of(unknown, orangeBottom),
            orangeBottom, List.of(document("오렌지", tonal)));

        // then
        assertThat(result).containsExactly(brownTop, orangeBottom);
    }

    @Test
    void 기준색이나_기준_종류가_다른_문서와_빈_색_배치는_사용하지_않는다() {
        // given
        List<RecommendationClothesResponse> baseline = List.of(blueTop, orangeBottom);
        OutfitColorVariant wrongType = OutfitColorVariant.of(
            ClothesType.TOP, "적갈색", "오렌지", "카멜", "아이보리");
        OutfitColorVariant wrongAnchor = OutfitColorVariant.of(
            ClothesType.BOTTOM, "적갈색", "핑크", "카멜", "아이보리");

        // when
        List<RecommendationClothesResponse> result = matcher.match(calm,
            List.of(blueTop, brownTop, orangeBottom), baseline, orangeBottom,
            List.of(document("핑크", tonal), document("오렌지", wrongType),
                document("오렌지", wrongAnchor), document("오렌지")));

        // then
        assertThat(result).isSameAs(baseline);
    }

    private RecommendationClothesResponse item(ClothesType type, String color, String thickness) {
        return new RecommendationClothesResponse(UUID.randomUUID(), color, null, type, List.of(
            new ClothesAttributeWithDefDto(UUID.randomUUID(), "색상", List.of(color), color),
            new ClothesAttributeWithDefDto(UUID.randomUUID(), "두께", List.of(thickness), thickness)));
    }

    private RecommendationClothesResponse protectedTop(String color, String name, String protection) {
        return new RecommendationClothesResponse(UUID.randomUUID(), color, null, ClothesType.TOP, List.of(
            new ClothesAttributeWithDefDto(UUID.randomUUID(), "색상", List.of(color), color),
            new ClothesAttributeWithDefDto(UUID.randomUUID(), name, List.of(protection), protection)));
    }

    private StyleKnowledgeMatch document(String baseColor, OutfitColorVariant... variants) {
        return new StyleKnowledgeMatch("guide", "배색", "내용", baseColor, CombinationType.TONAL,
            "WEB_OBSERVED", "https://example.com/guide", List.of(variants), 0.9);
    }
}
