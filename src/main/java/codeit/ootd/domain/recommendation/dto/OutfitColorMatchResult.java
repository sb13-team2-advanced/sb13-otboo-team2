package codeit.ootd.domain.recommendation.dto;

import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 배색 비교 후의 의상 목록과 선택 또는 검증 근거, 기존 코디의 변경 여부를 전달한다.
 * 기존 코디가 이미 문서의 배색과 맞으면 변경 없이도 근거를 가질 수 있다.
 */
public record OutfitColorMatchResult(
    List<RecommendationClothesResponse> clothes,
    Optional<OutfitColorEvidence> evidence,
    boolean changed
) {

    /**
     * 최종 의상 목록을 복사하고 근거의 유무는 Optional로 명시한다.
     */
    public OutfitColorMatchResult {
        clothes = List.copyOf(clothes);
        Objects.requireNonNull(evidence, "근거의 유무는 Optional로 전달해야 합니다.");
    }

    /**
     * 매칭에서 계산한 최종 의상, 근거 및 변경 여부로 결과를 생성한다.
     */
    public static OutfitColorMatchResult of(List<RecommendationClothesResponse> clothes,
        Optional<OutfitColorEvidence> evidence, boolean changed) {
        return new OutfitColorMatchResult(clothes, evidence, changed);
    }

    /**
     * 적용 가능한 배색이 없으면 기존 의상을 유지하고 근거 없는 결과를 생성한다.
     */
    public static OutfitColorMatchResult withoutEvidence(List<RecommendationClothesResponse> clothes) {
        return of(clothes, Optional.empty(), false);
    }
}
