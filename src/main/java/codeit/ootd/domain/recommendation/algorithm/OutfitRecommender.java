package codeit.ootd.domain.recommendation.algorithm;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * 기온별 목표 두께와의 차이가 작은 의상을 종류별로 선택한다.
 * 상하의가 모두 있으면 우선 사용하고, 없으면 원피스를 사용한다.
 * 두께는 얇음/보통/두꺼움으로 해석하고, 미등록 값은 보통으로 처리한다.
 * 동점은 의상 UUID 순으로 결정해 조회 순서에 영향을 받지 않는다.
 */
@Component
public class OutfitRecommender {

    private static final double COLD_TEMPERATURE = 10;
    private static final double WARM_TEMPERATURE = 23;
    private static final double OUTER_TEMPERATURE = 20;
    private static final int THIN = 1;
    private static final int NORMAL = 2;
    private static final int THICK = 3;

    public List<RecommendationClothesResponse> recommend(double temperature,
                                                         List<RecommendationClothesResponse> clothes) {

        int target = temperature < COLD_TEMPERATURE ? THICK
                : temperature >= WARM_TEMPERATURE ? THIN : NORMAL;

        Comparator<RecommendationClothesResponse> order = Comparator
                .comparingInt((RecommendationClothesResponse item) -> Math.abs(thickness(item) - target))
                .thenComparing(RecommendationClothesResponse::clothesId);

        List<RecommendationClothesResponse> result = new ArrayList<>();

        Optional<RecommendationClothesResponse> top = select(clothes, ClothesType.TOP, order);

        Optional<RecommendationClothesResponse> bottom = select(clothes, ClothesType.BOTTOM, order);

        Optional<RecommendationClothesResponse> dress = select(clothes, ClothesType.DRESS, order);

        if (top.isPresent() && bottom.isPresent()) {
            result.add(top.get());
            result.add(bottom.get());
        } else if (dress.isPresent()) {
            result.add(dress.get());
        } else {
            return List.of();
        }

        if (temperature < OUTER_TEMPERATURE) {
            select(clothes, ClothesType.OUTER, order).ifPresent(result::add);
        }

        select(clothes, ClothesType.SHOES, order).ifPresent(result::add);

        return List.copyOf(result);
    }

    private Optional<RecommendationClothesResponse> select(List<RecommendationClothesResponse> clothes,
                                                           ClothesType type, Comparator<RecommendationClothesResponse> order) {
        return clothes.stream().filter(item -> item.type() == type).min(order);
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
