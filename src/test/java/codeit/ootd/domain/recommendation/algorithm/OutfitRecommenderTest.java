package codeit.ootd.domain.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * 기온 경계, 속성 누락, 코디 구성과 동점 처리 정책을 검증한다.
 */
class OutfitRecommenderTest {
    private final OutfitRecommender recommender = new OutfitRecommender();

    /**
     * 다른 속성과 미등록 두께 값을 기본 두께로 처리하고 신발까지 조합하는지 검증한다.
     */
    @Test
    void 미등록_두께는_보통으로_처리하고_다른_속성은_무시한다() {
        // given
        RecommendationClothesResponse top = new RecommendationClothesResponse(
                UUID.randomUUID(), "상의", null, ClothesType.TOP,
                List.of(new ClothesAttributeWithDefDto(UUID.randomUUID(), "색상", List.of("검정"), "검정"),
                        new ClothesAttributeWithDefDto(UUID.randomUUID(), "두께", List.of("미등록"), "미등록")));
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");
        RecommendationClothesResponse shoes = item(ClothesType.SHOES, "보통");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(20,
                List.of(top, item(ClothesType.TOP, "두꺼움"), bottom, shoes));

        // then
        assertThat(result).containsExactly(top, bottom, shoes);
    }

    /**
     * 상의 없이 하의만 있는 경우에도 완전한 코디가 없으면 추천하지 않는지 검증한다.
     */
    @Test
    void 하의만_있으면_빈_목록을_반환한다() {
        // given
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(15, List.of(bottom));

        // then
        assertThat(result).isEmpty();
    }

    private static RecommendationClothesResponse item(ClothesType type, String thickness) {
        return new RecommendationClothesResponse(UUID.randomUUID(), "의상", null, type,
                List.of(new ClothesAttributeWithDefDto(UUID.randomUUID(), "두께",
                        List.of("얇음", "보통", "두꺼움"), thickness)));
    }

    @Test
    void 추운_날에는_두꺼운_상의와_아우터를_고른다() {
        // given

        RecommendationClothesResponse thin = item(ClothesType.TOP, "얇음");
        RecommendationClothesResponse thick = item(ClothesType.TOP, "두꺼움");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "두꺼움");
        RecommendationClothesResponse outer = item(ClothesType.OUTER, "두꺼움");
        // when
        List<RecommendationClothesResponse> result = recommender.recommend(5, List.of(thin, thick, bottom, outer));
        // then
        assertThat(result).containsExactly(thick, bottom, outer);
    }

    @Test
    void 더운_날에는_얇은_옷을_고르고_아우터를_제외한다() {
        // given
        RecommendationClothesResponse thin = item(ClothesType.TOP, "얇음");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "얇음");
        // when
        List<RecommendationClothesResponse> result = recommender.recommend(23, List.of(
                item(ClothesType.TOP, "두꺼움"), thin, bottom, item(ClothesType.OUTER, "보통")));
        // then
        assertThat(result).containsExactly(thin, bottom);
    }

    @Test
    void 상하의가_없으면_원피스를_선택한다() {
        // given
        RecommendationClothesResponse dress = item(ClothesType.DRESS, "보통");
        // when
        List<RecommendationClothesResponse> result = recommender.recommend(20, List.of(dress, item(ClothesType.OUTER, "보통")));
        // then
        assertThat(result).containsExactly(dress);
    }

    @Test
    void 코디를_구성할_수_없으면_빈_목록을_반환한다() {
        // given
        RecommendationClothesResponse top = item(ClothesType.TOP, "보통");
        // when
        List<RecommendationClothesResponse> result = recommender.recommend(15, List.of(top));
        // then
        assertThat(result).isEmpty();
        assertThat(recommender.recommend(15, List.of())).isEmpty();
    }

    @Test
    void 두께가_없으면_보통으로_평가하고_동점은_조회순서에_영향받지_않는다() {
        // given
        RecommendationClothesResponse first = new RecommendationClothesResponse(new UUID(0, 1), "상의", null, ClothesType.TOP, List.of());
        RecommendationClothesResponse second = new RecommendationClothesResponse(new UUID(0, 2), "상의", null, ClothesType.TOP, List.of());
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");
        // when
        List<RecommendationClothesResponse> result = recommender.recommend(10, List.of(second, bottom, first));
        // then

        assertThat(result).containsExactly(first, bottom);
    }
}
