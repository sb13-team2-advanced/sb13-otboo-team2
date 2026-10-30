package codeit.ootd.domain.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    /**
     * 추천 대상이 아닌 부가 의상은 두께와 관계없이 제외한다.
     */
    @ParameterizedTest
    @EnumSource(value = ClothesType.class, names = {"UNDERWEAR", "ACCESSORY",
            "SOCKS", "HAT", "BAG", "SCARF", "ETC"})
    void 부가_의상은_추천에서_제외한다(ClothesType type) {
        // given
        RecommendationClothesResponse dress = item(ClothesType.DRESS, "보통");
        RecommendationClothesResponse suitable = item(type, "보통");
        RecommendationClothesResponse unsuitable = item(type, "두꺼움");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(20,
                List.of(dress, suitable, unsuitable));

        // then
        assertThat(result).containsExactly(dress);
    }

    /**
     * 상하의와 원피스를 모두 보유하면 둘 중 하나만 선택하고 부가 의상은 유지한다.
     */
    @Test
    void 상하의와_원피스_구성을_랜덤으로_선택한다() {
        // given
        RecommendationClothesResponse top = item(ClothesType.TOP, "보통");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");
        RecommendationClothesResponse dress = item(ClothesType.DRESS, "보통");
        RecommendationClothesResponse outer = item(ClothesType.OUTER, "보통");
        RecommendationClothesResponse underwear = item(ClothesType.UNDERWEAR, "보통");
        RecommendationClothesResponse accessory = item(ClothesType.ACCESSORY, "보통");
        RecommendationClothesResponse shoes = item(ClothesType.SHOES, "보통");
        List<RecommendationClothesResponse> clothes = List.of(top, bottom, dress, outer,
                underwear, accessory, shoes);
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        try (var mocked = mockStatic(ThreadLocalRandom.class)) {
            mocked.when(ThreadLocalRandom::current).thenReturn(random);
            when(random.nextBoolean()).thenReturn(false, true);

            // when
            List<RecommendationClothesResponse> separates = recommender.recommend(15, clothes);
            List<RecommendationClothesResponse> onePiece = recommender.recommend(15, clothes);

            // then
            assertThat(separates).containsExactly(top, bottom, outer, shoes);
            assertThat(onePiece).containsExactly(dress, outer, shoes);
        }
    }

    /**
     * 속옷과 액세서리만으로는 기본 코디를 구성하지 않는다.
     */
    @Test
    void 부가_의상만_있으면_빈_목록을_반환한다() {
        // given
        List<RecommendationClothesResponse> clothes = List.of(item(ClothesType.UNDERWEAR, "보통"),
                item(ClothesType.ACCESSORY, "보통"), item(ClothesType.SHOES, "보통"));

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(15, clothes);

        // then
        assertThat(result).isEmpty();
    }

    /**
     * 추운 날 얇은 원피스보다 두꺼운 상하의 조합을 우선한다.
     */
    @Test
    void 원피스보다_상하의의_두께가_적합하면_상하의를_선택한다() {
        // given
        RecommendationClothesResponse top = item(ClothesType.TOP, "두꺼움");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "두꺼움");
        RecommendationClothesResponse dress = item(ClothesType.DRESS, "얇음");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(5, List.of(top, bottom, dress));

        // then
        assertThat(result).containsExactly(top, bottom);
    }

    /**
     * 상하의 차이 평균의 소수 부분을 유지하여 더 적합한 원피스를 선택한다.
     */
    @Test
    void 상하의_차이의_평균보다_원피스_차이가_작으면_원피스를_선택한다() {
        // given
        RecommendationClothesResponse top = item(ClothesType.TOP, "보통");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "얇음");
        RecommendationClothesResponse dress = item(ClothesType.DRESS, "보통");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(5, List.of(top, bottom, dress));

        // then
        assertThat(result).containsExactly(dress);
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

    /**
     * 난수 선택 위치를 제어하여 같은 적합도의 상하의 네 조합이 모두 선택 가능한지 검증한다.
     */
    @Test
    void 동점_후보에서_상하의를_각각_랜덤으로_선택한다() {
        // given
        RecommendationClothesResponse top1 = item(ClothesType.TOP, "보통");
        RecommendationClothesResponse top2 = item(ClothesType.TOP, "보통");
        RecommendationClothesResponse bottom1 = item(ClothesType.BOTTOM, "보통");
        RecommendationClothesResponse bottom2 = item(ClothesType.BOTTOM, "보통");
        List<RecommendationClothesResponse> clothes = List.of(top1, top2, bottom1, bottom2,
                item(ClothesType.TOP, "두꺼움"));
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        try (var mocked = mockStatic(ThreadLocalRandom.class)) {
            mocked.when(ThreadLocalRandom::current).thenReturn(random);
            when(random.nextInt(2)).thenReturn(0, 0, 0, 1, 1, 0, 1, 1);

            // when then
            assertThat(recommender.recommend(20, clothes)).containsExactly(top1, bottom1);
            assertThat(recommender.recommend(20, clothes)).containsExactly(top1, bottom2);
            assertThat(recommender.recommend(20, clothes)).containsExactly(top2, bottom1);
            assertThat(recommender.recommend(20, clothes)).containsExactly(top2, bottom2);
        }
    }

    @Test
    void 두께가_없으면_보통으로_평가한다() {
        // given
        RecommendationClothesResponse top = new RecommendationClothesResponse(UUID.randomUUID(),
                "상의", null, ClothesType.TOP, List.of());
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(10,
                List.of(top, bottom, item(ClothesType.TOP, "얇음")));

        // then
        assertThat(result).containsExactly(top, bottom);
    }
}
