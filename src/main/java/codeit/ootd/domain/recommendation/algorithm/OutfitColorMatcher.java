package codeit.ootd.domain.recommendation.algorithm;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 검색한 한 색 배치 안에서 날씨 동점 후보를 비교하여 기존 코디보다 많은 의상 색상이 맞을 때 교체한다.
 */
@Component
@RequiredArgsConstructor
public class OutfitColorMatcher {

    private static final int MINIMUM_MATCHED_COLORS = 2;

    private final OutfitRecommender weatherRecommender;
    private final ClothesColorResolver colorResolver;

    /**
     * 기준 의상은 고정하고 검색 순서대로 색 배치를 비교한다. 상의·하의·아우터만 색을 평가하며
     * 문서가 종류를 구분하지 않은 소품 색상은 신발 색상으로 추정하지 않는다.
     * 같은 색 배치에서 최소 두 의상이 맞고 기존 코디의 최대 일치 개수보다 개선될 때만 교체한다.
     * 후보를 종류별로 탐색하므로 모든 의상의 조합을 생성하지 않는다.
     */
    public List<RecommendationClothesResponse> match(RecommendationConditions conditions,
        List<RecommendationClothesResponse> clothes, List<RecommendationClothesResponse> baseline,
        RecommendationClothesResponse anchor, List<StyleKnowledgeMatch> documents) {

        String anchorColor = colorResolver.resolve(anchor).orElseThrow();

        List<OutfitColorVariant> variants = documents.stream()
            .filter(document -> anchorColor.equals(document.baseColor()))
            .flatMap(document -> document.outfitVariants().stream())
            .filter(variant -> variant.selectedType() == anchor.type())
            .filter(variant -> anchorColor.equals(color(variant, anchor.type())))
            .toList();

        if (variants.isEmpty()) {
            return baseline;
        }

        Map<ClothesType, List<RecommendationClothesResponse>> candidates = new EnumMap<>(ClothesType.class);
        for (RecommendationClothesResponse item : baseline) {
            candidates.put(item.type(), weatherRecommender.weatherCandidates(conditions, clothes, item.type()));
        }

        int bestScore = Math.max(MINIMUM_MATCHED_COLORS - 1,
            variants.stream().mapToInt(variant -> score(baseline, variant)).max().orElse(0));

        List<RecommendationClothesResponse> best = baseline;
        for (OutfitColorVariant variant : variants) {
            List<RecommendationClothesResponse> candidate = baseline.stream()
                .map(item -> item.clothesId().equals(anchor.clothesId()) ? item
                    : select(item, candidates.get(item.type()), color(variant, item.type())))
                .toList();

            int candidateScore = score(candidate, variant);
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                best = candidate;
            }
        }

        return best;
    }

    /**
     * 기존 의상이 이미 목표색이면 유지하고 아니면 같은 날씨 적합도를 가진 목표색 의상만 선택한다.
     */
    private RecommendationClothesResponse select(RecommendationClothesResponse original,
        List<RecommendationClothesResponse> candidates, String targetColor) {

        if (targetColor == null || matchesColor(original, targetColor)) {
            return original;
        }

        return candidates.stream()
            .filter(candidate -> matchesColor(candidate, targetColor))
            .findFirst()
            .orElse(original);
    }

    /**
     * 한 문서의 한 색 배치와 일치한 상의·하의·아우터 개수를 계산하고 미지원 색상은 세지 않는다.
     */
    private int score(List<RecommendationClothesResponse> outfit, OutfitColorVariant variant) {
        return (int) outfit.stream()
            .filter(item -> matchesColor(item, color(variant, item.type())))
            .count();
    }

    /**
     * 문서가 지정한 색상이 있고 의상의 정규화한 색상이 같을 때만 일치로 판단한다.
     */
    private boolean matchesColor(RecommendationClothesResponse item, String expected) {
        return expected != null && colorResolver.resolve(item).filter(expected::equals).isPresent();
    }

    /**
     * 문서에서 명시한 의상 종류의 색상만 반환한다.
     */
    private String color(OutfitColorVariant variant, ClothesType type) {
        return switch (type) {
            case TOP -> variant.topColor();
            case BOTTOM -> variant.bottomColor();
            case OUTER -> variant.outerColor();
            default -> null;
        };
    }
}
