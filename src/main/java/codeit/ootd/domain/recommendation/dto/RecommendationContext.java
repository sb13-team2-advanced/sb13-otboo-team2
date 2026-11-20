package codeit.ootd.domain.recommendation.dto;

import codeit.ootd.domain.recommendation.algorithm.RecommendationConditions;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import java.util.List;
import java.util.Optional;

/**
 * DB 조회를 마친 뒤 날씨 조건과 본인 옷장의 의상만 추천 단계에 전달한다.
 */
public record RecommendationContext(
    Optional<RecommendationConditions> conditions,
    List<RecommendationClothesResponse> candidates
) {

    /**
     * 의상 목록을 복사해 조회 이후의 추천 과정에서 입력 목록이 변경되지 않도록 한다.
     */
    public RecommendationContext {
        candidates = List.copyOf(candidates);
    }

    /**
     * 예보가 없거나 옷장이 비면 비어 있는 조건과 함께 조회 결과를 생성한다.
     */
    public static RecommendationContext of(Optional<RecommendationConditions> conditions,
        List<RecommendationClothesResponse> candidates) {
        return new RecommendationContext(conditions, candidates);
    }
}
