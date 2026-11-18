package codeit.ootd.domain.recommendation.algorithm;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * 목표 두께와의 차이를 우선하고 강수·바람에 맞는 방수·방풍 성능으로 후보의 순위를 정한다.
 * 상하의 평균 점수와 원피스 점수를 비교하고 동점인 후보와 구성은 무작위로 선택한다.
 * 서늘하거나 강수·바람 대비가 필요한 날에는 아우터를 추가하며 각 종류는 최대 한 벌을 선택한다.
 * 두께와 기능 속성이 없거나 미등록 값이면 보통으로 평가한다.
 */
@Component
public class OutfitRecommender {
    private static final double COLD_TEMPERATURE = 10;
    private static final double WARM_TEMPERATURE = 23;
    private static final double OUTER_TEMPERATURE = 20;
    private static final int THIN = 1;
    private static final int NORMAL = 2;
    private static final int THICK = 3;
    private static final double SEPARATES_COUNT = 2.0;
    private static final int THICKNESS_WEIGHT = 5;
    private static final int HIGH_PROTECTION_PENALTY = 0;
    private static final int NORMAL_PROTECTION_PENALTY = 1;
    private static final int LOW_PROTECTION_PENALTY = 2;
    private static final String THICKNESS_ATTRIBUTE = "두께";
    private static final String WATERPROOF_ATTRIBUTE = "방수";
    private static final String WINDPROOF_ATTRIBUTE = "방풍";

    /**
     * 사용자별 날씨 조건에 적합한 의상으로 완성 가능한 코디를 반환한다.
     */
    public List<RecommendationClothesResponse> recommend(RecommendationConditions conditions,
            List<RecommendationClothesResponse> clothes) {

        double temperature = conditions.temperature();
        int target = temperature < COLD_TEMPERATURE ? THICK
                : temperature >= WARM_TEMPERATURE ? THIN : NORMAL;

        Optional<RecommendationClothesResponse> top = select(clothes, ClothesType.TOP, target, conditions);
        Optional<RecommendationClothesResponse> bottom = select(clothes, ClothesType.BOTTOM, target, conditions);
        Optional<RecommendationClothesResponse> dress = select(clothes, ClothesType.DRESS, target, conditions);
        boolean hasSeparates = top.isPresent() && bottom.isPresent();
        List<RecommendationClothesResponse> result = new ArrayList<>();

        if (hasSeparates && (dress.isEmpty()
                || preferSeparates(top.get(), bottom.get(), dress.get(), target, conditions))) {
            result.add(top.get());
            result.add(bottom.get());
        } else {
            if (dress.isEmpty()) {
                return List.of();
            }
            result.add(dress.get());
        }

        if (temperature < OUTER_TEMPERATURE || (temperature < WARM_TEMPERATURE
                && (conditions.precipitationExpected() || conditions.windy()))) {
            int outerTarget = temperature >= OUTER_TEMPERATURE ? THIN : target;
            select(clothes, ClothesType.OUTER, outerTarget, conditions).ifPresent(result::add);
        }

        select(clothes, ClothesType.SHOES, target, conditions).ifPresent(result::add);

        return List.copyOf(result);
    }

    /**
     * 상하의의 평균 부적합 점수와 원피스 점수를 비교하며 동점일 때만 무작위 선택한다.
     */
    private boolean preferSeparates(RecommendationClothesResponse top,
            RecommendationClothesResponse bottom, RecommendationClothesResponse dress, int target,
            RecommendationConditions conditions) {

        double separatesDifference = (score(top, target, conditions)
                + score(bottom, target, conditions)) / SEPARATES_COUNT;

        int dressDifference = score(dress, target, conditions);

        return separatesDifference < dressDifference
                || (separatesDifference == dressDifference && !ThreadLocalRandom.current().nextBoolean());
    }

    /**
     * 두께와 날씨 기능의 부적합 점수가 최소인 후보 중 균등한 확률로 하나를 선택한다.
     */
    private Optional<RecommendationClothesResponse> select(List<RecommendationClothesResponse> clothes,
            ClothesType type, int target, RecommendationConditions conditions) {

        List<RecommendationClothesResponse> category = clothes.stream()
                .filter(item -> item.type() == type).toList();
        if (category.isEmpty()) {
            return Optional.empty();
        }

        int minimum = category.stream().mapToInt(item -> score(item, target, conditions))
                .min().orElseThrow();

        List<RecommendationClothesResponse> candidates = category.stream()
                .filter(item -> score(item, target, conditions) == minimum).toList();

        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    /**
     * 두께 차이 한 단계가 방수·방풍 감점의 합보다 크게 하여 기능 때문에 과도한 두께를 고르지 않는다.
     */
    private int score(RecommendationClothesResponse item, int target, RecommendationConditions conditions) {
        int score = Math.abs(thickness(item) - target) * THICKNESS_WEIGHT;
        if (conditions.precipitationExpected()) {
            score += protectionPenalty(item, WATERPROOF_ATTRIBUTE);
        }
        if (conditions.windy()) {
            score += protectionPenalty(item, WINDPROOF_ATTRIBUTE);
        }
        return score;
    }

    /**
     * 방수·방풍의 높음, 보통, 낮음을 감점으로 변환하고 누락 또는 미등록 값은 보통으로 처리한다.
     */
    private int protectionPenalty(RecommendationClothesResponse item, String definitionName) {
        return item.attributes().stream()
                .filter(attribute -> definitionName.equals(attribute.definitionName()))
                .mapToInt(attribute -> switch (attribute.value()) {
                    case "높음" -> HIGH_PROTECTION_PENALTY;
                    case "낮음" -> LOW_PROTECTION_PENALTY;
                    default -> NORMAL_PROTECTION_PENALTY;
                }).findFirst().orElse(NORMAL_PROTECTION_PENALTY);
    }

    /**
     * 두께 속성을 단계로 변환하고 누락 또는 미등록 값은 보통으로 처리한다.
     */
    private int thickness(RecommendationClothesResponse item) {
        return item.attributes().stream()
                .filter(attribute -> THICKNESS_ATTRIBUTE.equals(attribute.definitionName()))
                .mapToInt(attribute -> switch (attribute.value()) {
                    case "얇음" -> THIN;
                    case "두꺼움" -> THICK;
                    default -> NORMAL;
                }).findFirst().orElse(NORMAL);
    }
}
