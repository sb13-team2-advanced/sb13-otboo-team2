package codeit.ootd.domain.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 기온 경계, 속성 누락, 코디 구성과 동점 처리 정책을 검증한다.
 */
class OutfitRecommenderTest {
    private final OutfitRecommender recommender = new OutfitRecommender();

    /**
     * 비·눈과 바람 조건에서 같은 두께의 기능성 의상을 우선하는지 검증한다.
     */
    @ParameterizedTest
    @CsvSource({"0.6, 0, 0, 0, 방수", "0, 0.1, 0, 0, 방수", "0, 0, 0.1, 0, 방수",
            "0, 0, 0, 4, 방풍"})
    void 날씨에_맞는_기능_성능이_높은_의상을_고른다(double probability, double rain,
            double snow, double wind, String attribute) {
        // given
        RecommendationClothesResponse high = protectedItem(ClothesType.TOP, "보통", attribute, "높음");
        RecommendationClothesResponse normal = protectedItem(ClothesType.TOP, "보통", attribute, "보통");
        RecommendationClothesResponse low = protectedItem(ClothesType.TOP, "보통", attribute, "낮음");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");
        RecommendationConditions conditions = RecommendationConditions.of(18, probability, rain, snow, wind);

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(conditions,
                List.of(low, normal, high, bottom));

        // then
        assertThat(result).containsExactly(high, bottom);
    }

    @Test
    void 강수와_바람을_함께_평가하고_기능보다_적합한_두께를_우선한다() {
        // given
        RecommendationClothesResponse protectedThin = protectedItem(ClothesType.TOP, "얇음", "방수", "높음");
        protectedThin = withAttribute(protectedThin, "방풍", "높음");
        RecommendationClothesResponse normal = protectedItem(ClothesType.TOP, "보통", "방수", "낮음");
        normal = withAttribute(normal, "방풍", "낮음");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(
                RecommendationConditions.of(18, 0.6, 0, 0, 4), List.of(protectedThin, normal, bottom));

        // then
        assertThat(result).containsExactly(normal, bottom);
    }

    @Test
    void 강수와_바람의_기능_점수를_합산한다() {
        // given
        RecommendationClothesResponse waterproof = protectedItem(ClothesType.TOP, "보통", "방수", "높음");
        RecommendationClothesResponse both = withAttribute(waterproof, "방풍", "높음");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(
                RecommendationConditions.of(18, 0.6, 0, 0, 4), List.of(waterproof, both, bottom));

        // then
        assertThat(result).containsExactly(both, bottom);
    }

    @Test
    void 비가_오면_신발도_방수_성능으로_고른다() {
        // given
        RecommendationClothesResponse dress = item(ClothesType.DRESS, "보통");
        RecommendationClothesResponse shoes = protectedItem(ClothesType.SHOES, "보통", "방수", "높음");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(
                RecommendationConditions.of(20, 0.6, 0, 0, 0),
                List.of(dress, item(ClothesType.SHOES, "보통"), shoes));

        // then
        assertThat(result).containsExactly(dress, shoes);
    }

    @ParameterizedTest
    @CsvSource({"미등록", "보통"})
    void 기능이_없거나_미등록이면_보통으로_평가한다(String protection) {
        // given
        RecommendationClothesResponse missing = item(ClothesType.TOP, "보통");
        RecommendationClothesResponse unknown = protectedItem(ClothesType.TOP, "보통", "방수", protection);
        RecommendationClothesResponse low = protectedItem(ClothesType.TOP, "보통", "방수", "낮음");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        try (var mocked = mockStatic(ThreadLocalRandom.class)) {
            mocked.when(ThreadLocalRandom::current).thenReturn(random);
            when(random.nextInt(2)).thenReturn(0, 1);

            // when
            List<RecommendationClothesResponse> first = recommender.recommend(
                    RecommendationConditions.of(20, 0.6, 0, 0, 0), List.of(missing, unknown, low, bottom));
            List<RecommendationClothesResponse> second = recommender.recommend(
                    RecommendationConditions.of(20, 0.6, 0, 0, 0), List.of(missing, unknown, low, bottom));

            // then
            assertThat(first).containsExactly(missing, bottom);
            assertThat(second).containsExactly(unknown, bottom);
        }
    }

    @Test
    void 맑고_바람이_약하면_방수와_방풍으로_우선순위를_바꾸지_않는다() {
        // given
        RecommendationClothesResponse low = protectedItem(ClothesType.TOP, "보통", "방수", "낮음");
        low = withAttribute(low, "방풍", "낮음");
        RecommendationClothesResponse high = protectedItem(ClothesType.TOP, "보통", "방수", "높음");
        high = withAttribute(high, "방풍", "높음");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        try (var mocked = mockStatic(ThreadLocalRandom.class)) {
            mocked.when(ThreadLocalRandom::current).thenReturn(random);
            when(random.nextInt(2)).thenReturn(0, 1);

            // when
            List<RecommendationClothesResponse> first = recommender.recommend(
                    calm(20), List.of(low, high, bottom));
            List<RecommendationClothesResponse> second = recommender.recommend(
                    calm(20), List.of(low, high, bottom));

            // then
            assertThat(first).containsExactly(low, bottom);
            assertThat(second).containsExactly(high, bottom);
        }
    }

    @ParameterizedTest
    @CsvSource({"20, 0.6, 0, true", "22.9, 0.6, 0, true", "22, 0, 4, true",
            "23, 0.6, 4, false", "30, 0.6, 8, false", "20, 0, 0, false"})
    void 덥지_않을_때만_강수와_바람에_대비한_얇은_아우터를_추가한다(double temperature,
            double probability, double wind, boolean includeOuter) {
        // given
        RecommendationClothesResponse dress = item(ClothesType.DRESS, "보통");
        RecommendationClothesResponse outer = item(ClothesType.OUTER, "얇음");
        RecommendationClothesResponse thickOuter = item(ClothesType.OUTER, "두꺼움");
        RecommendationConditions conditions = RecommendationConditions.of(temperature, probability, 0, 0, wind);

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(conditions,
                List.of(dress, outer, thickOuter));

        // then
        assertThat(result.contains(outer)).isEqualTo(includeOuter);
        assertThat(result).doesNotContain(thickOuter);
    }

    @Test
    void 같은_두께의_코디는_날씨_기능이_적합한_구성을_선택한다() {
        // given
        RecommendationClothesResponse top = item(ClothesType.TOP, "보통");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");
        RecommendationClothesResponse dress = protectedItem(ClothesType.DRESS, "보통", "방수", "높음");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(
                RecommendationConditions.of(20, 0.6, 0, 0, 0), List.of(top, bottom, dress));

        // then
        assertThat(result).containsExactly(dress);
    }

    /**
     * 강수와 바람의 영향이 없는 추천 조건을 만든다.
     */
    private static RecommendationConditions calm(double temperature) {
        return RecommendationConditions.of(temperature, 0, 0, 0, 0);
    }

    /**
     * 두께가 있는 의상에 날씨 기능 속성을 추가해 테스트 후보를 만든다.
     */
    private static RecommendationClothesResponse protectedItem(ClothesType type, String thickness,
            String attribute, String value) {
        return withAttribute(item(type, thickness), attribute, value);
    }

    /**
     * 기존 속성과 새 기능 속성을 함께 가진 별도의 의상 후보를 만든다.
     */
    private static RecommendationClothesResponse withAttribute(RecommendationClothesResponse item,
            String name, String value) {
        List<ClothesAttributeWithDefDto> attributes = new ArrayList<>(item.attributes());
        attributes.add(new ClothesAttributeWithDefDto(UUID.randomUUID(), name,
                List.of("높음", "보통", "낮음"), value));
        return new RecommendationClothesResponse(UUID.randomUUID(), item.name(), item.imageUrl(),
                item.type(), List.copyOf(attributes));
    }

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
        List<RecommendationClothesResponse> result = recommender.recommend(calm(20),
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
        List<RecommendationClothesResponse> result = recommender.recommend(calm(15), List.of(bottom));

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
        List<RecommendationClothesResponse> result = recommender.recommend(calm(20),
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
            List<RecommendationClothesResponse> separates = recommender.recommend(calm(15), clothes);
            List<RecommendationClothesResponse> onePiece = recommender.recommend(calm(15), clothes);

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
        List<RecommendationClothesResponse> result = recommender.recommend(calm(15), clothes);

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
        List<RecommendationClothesResponse> result = recommender.recommend(calm(5), List.of(top, bottom, dress));

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
        List<RecommendationClothesResponse> result = recommender.recommend(calm(5), List.of(top, bottom, dress));

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
        List<RecommendationClothesResponse> result = recommender.recommend(calm(5), List.of(thin, thick, bottom, outer));
        // then
        assertThat(result).containsExactly(thick, bottom, outer);
    }

    @Test
    void 더운_날에는_얇은_옷을_고르고_아우터를_제외한다() {
        // given
        RecommendationClothesResponse thin = item(ClothesType.TOP, "얇음");
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "얇음");
        // when
        List<RecommendationClothesResponse> result = recommender.recommend(calm(23), List.of(
                item(ClothesType.TOP, "두꺼움"), thin, bottom, item(ClothesType.OUTER, "보통")));
        // then
        assertThat(result).containsExactly(thin, bottom);
    }

    @Test
    void 상하의가_없으면_원피스를_선택한다() {
        // given
        RecommendationClothesResponse dress = item(ClothesType.DRESS, "보통");
        // when
        List<RecommendationClothesResponse> result = recommender.recommend(calm(20), List.of(dress, item(ClothesType.OUTER, "보통")));
        // then
        assertThat(result).containsExactly(dress);
    }

    @Test
    void 코디를_구성할_수_없으면_빈_목록을_반환한다() {
        // given
        RecommendationClothesResponse top = item(ClothesType.TOP, "보통");
        // when
        List<RecommendationClothesResponse> result = recommender.recommend(calm(15), List.of(top));
        // then
        assertThat(result).isEmpty();
        assertThat(recommender.recommend(calm(15), List.of())).isEmpty();
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
            assertThat(recommender.recommend(calm(20), clothes)).containsExactly(top1, bottom1);
            assertThat(recommender.recommend(calm(20), clothes)).containsExactly(top1, bottom2);
            assertThat(recommender.recommend(calm(20), clothes)).containsExactly(top2, bottom1);
            assertThat(recommender.recommend(calm(20), clothes)).containsExactly(top2, bottom2);
        }
    }

    @Test
    void 두께가_없으면_보통으로_평가한다() {
        // given
        RecommendationClothesResponse top = new RecommendationClothesResponse(UUID.randomUUID(),
                "상의", null, ClothesType.TOP, List.of());
        RecommendationClothesResponse bottom = item(ClothesType.BOTTOM, "보통");

        // when
        List<RecommendationClothesResponse> result = recommender.recommend(calm(10),
                List.of(top, bottom, item(ClothesType.TOP, "얇음")));

        // then
        assertThat(result).containsExactly(top, bottom);
    }
}
