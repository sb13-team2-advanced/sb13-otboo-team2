package codeit.ootd.domain.recommendation.algorithm;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * 종류별로 기온의 목표 두께에 가장 가까운 후보 중 하나를 무작위 선택한다.
 * 상하의의 평균 두께 차이와 원피스의 두께 차이를 비교해 더 적합한 구성을 선택한다.
 * 적합도가 같으면 구성을 무작위 선택하며 신발만 추가한다.
 * 아우터는 20도 미만일 때만 포함하며 각 종류는 최대 한 벌을 선택한다.
 * 두께가 없거나 미등록 값이면 보통으로 처리하며 같은 조합이 다시 나올 수 있다.
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

    /**
     * 기온에 가장 적합한 종류별 후보 중 랜덤으로 뽑아 완성 가능한 코디를 반환한다.
     */
    public List<RecommendationClothesResponse> recommend(double temperature,
            List<RecommendationClothesResponse> clothes) {

        int target = temperature < COLD_TEMPERATURE ? THICK
                : temperature >= WARM_TEMPERATURE ? THIN : NORMAL;
        Optional<RecommendationClothesResponse> top = select(clothes, ClothesType.TOP, target);
        Optional<RecommendationClothesResponse> bottom = select(clothes, ClothesType.BOTTOM, target);
        Optional<RecommendationClothesResponse> dress = select(clothes, ClothesType.DRESS, target);
        boolean hasSeparates = top.isPresent() && bottom.isPresent();
        List<RecommendationClothesResponse> result = new ArrayList<>();

        if (hasSeparates && (dress.isEmpty()
                || preferSeparates(top.get(), bottom.get(), dress.get(), target))) {
            result.add(top.get());
            result.add(bottom.get());
        } else {
            if (dress.isEmpty()) {
                return List.of();
            }
            result.add(dress.get());
        }

        if (temperature < OUTER_TEMPERATURE) {
            select(clothes, ClothesType.OUTER, target).ifPresent(result::add);
        }

        select(clothes, ClothesType.SHOES, target).ifPresent(result::add);

        return List.copyOf(result);
    }

    /**
     * 상하의의 절대 두께 차이 평균과 원피스의 절대 두께 차이를 비교하며 동점일 때만 무작위 선택한다.
     */
    private boolean preferSeparates(RecommendationClothesResponse top,
            RecommendationClothesResponse bottom, RecommendationClothesResponse dress, int target) {

        double separatesDifference = (Math.abs(thickness(top) - target)
                + Math.abs(thickness(bottom) - target)) / SEPARATES_COUNT;

        int dressDifference = Math.abs(thickness(dress) - target);

        return separatesDifference < dressDifference
                || (separatesDifference == dressDifference && !ThreadLocalRandom.current().nextBoolean());
    }

    /**
     * 목표 두께와의 차이가 최소인 후보만 남기고 균등한 확률로 하나를 선택한다.
     */
    private Optional<RecommendationClothesResponse> select(List<RecommendationClothesResponse> clothes,
            ClothesType type, int target) {

        List<RecommendationClothesResponse> category = clothes.stream()
                .filter(item -> item.type() == type).toList();
        if (category.isEmpty()) {
            return Optional.empty();
        }

        int minimum = category.stream().mapToInt(item -> Math.abs(thickness(item) - target))
                .min().orElseThrow();

        List<RecommendationClothesResponse> candidates = category.stream()
                .filter(item -> Math.abs(thickness(item) - target) == minimum).toList();

        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    private int thickness(RecommendationClothesResponse item) {
        return item.attributes().stream()
                .filter(attribute -> "두께".equals(attribute.definitionName()))
                .mapToInt(attribute -> switch (attribute.value()) {
                    case "얇음" -> THIN;
                    case "두꺼움" -> THICK;
                    default -> NORMAL;
                }).findFirst().orElse(NORMAL);
    }
}
