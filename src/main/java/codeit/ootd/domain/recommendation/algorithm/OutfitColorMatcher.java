package codeit.ootd.domain.recommendation.algorithm;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.OutfitColorEvidence;
import codeit.ootd.domain.recommendation.dto.OutfitColorMatchResult;
import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * 기존 코디가 이미 최소 두 의상 이상 일치하면 교체 없이도 해당 문서와 색 배치를 근거로 반환한다.
     * 같은 점수의 근거는 검색 문서와 문서 내 색 배치의 순서대로 우선한다.
     */
    public OutfitColorMatchResult match(RecommendationConditions conditions,
        List<RecommendationClothesResponse> clothes, List<RecommendationClothesResponse> baseline,
        RecommendationClothesResponse anchor, List<StyleKnowledgeMatch> documents) {

        String anchorColor = colorResolver.resolve(anchor).orElseThrow();

        List<DocumentVariant> variants = documents.stream()
            .filter(document -> anchorColor.equals(document.baseColor()))
            .flatMap(document -> document.outfitVariants().stream()
                .map(variant -> DocumentVariant.of(document, variant)))
            .filter(candidate -> candidate.variant().selectedType() == anchor.type())
            .filter(candidate -> anchorColor.equals(color(candidate.variant(), anchor.type())))
            .toList();

        if (variants.isEmpty()) {
            return OutfitColorMatchResult.withoutEvidence(baseline);
        }

        Map<ClothesType, List<RecommendationClothesResponse>> candidates = new EnumMap<>(ClothesType.class);
        for (RecommendationClothesResponse item : baseline) {
            candidates.put(item.type(), weatherRecommender.weatherCandidates(conditions, clothes, item.type()));
        }

        int bestScore = MINIMUM_MATCHED_COLORS - 1;

        OutfitColorEvidence bestEvidence = null;

        for (DocumentVariant candidate : variants) {
            int baselineScore = score(baseline, candidate.variant());
            if (baselineScore > bestScore) {
                bestScore = baselineScore;
                bestEvidence = evidence(candidate, baseline);
            }
        }

        List<RecommendationClothesResponse> best = baseline;

        boolean changed = false;

        for (DocumentVariant documentVariant : variants) {
            OutfitColorVariant variant = documentVariant.variant();
            List<RecommendationClothesResponse> candidate = baseline.stream()
                .map(item -> item.clothesId().equals(anchor.clothesId()) ? item
                    : select(item, candidates.get(item.type()), color(variant, item.type())))
                .toList();

            int candidateScore = score(candidate, variant);
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                best = candidate;
                bestEvidence = evidence(documentVariant, candidate);
                changed = true;
            }
        }

        return OutfitColorMatchResult.of(best, Optional.ofNullable(bestEvidence), changed);
    }

    /**
     * 최종 코디의 상의·하의·아우터 중 선택한 색 배치와 실제로 일치하는 의상만 근거에 연결한다.
     */
    private OutfitColorEvidence evidence(DocumentVariant candidate,
        List<RecommendationClothesResponse> outfit) {
        return OutfitColorEvidence.of(candidate.document(), candidate.variant(), outfit.stream()
            .filter(item -> matchesColor(item, color(candidate.variant(), item.type())))
            .map(RecommendationClothesResponse::clothesId)
            .toList());
    }

    /**
     * 검색 문서를 펼칠 때 각 색 배치의 원본 문서와 검색 순서를 보존한다.
     */
    private record DocumentVariant(StyleKnowledgeMatch document, OutfitColorVariant variant) {

        /**
         * 한 색 배치와 원본 검색 문서를 함께 묶는다.
         */
        private static DocumentVariant of(StyleKnowledgeMatch document, OutfitColorVariant variant) {
            return new DocumentVariant(document, variant);
        }
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
