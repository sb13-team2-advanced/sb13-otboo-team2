package codeit.ootd.domain.recommendation.dto;

import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 최종 코디와 일치한 배색 문서·색 배치 및 실제로 색상이 맞는 의상 ID를 전달한다.
 * 문서에 포함되어 있어도 최종 코디에서 일치하지 않은 의상이나 소품은 근거 ID에 넣지 않는다.
 */
public record OutfitColorEvidence(
    StyleKnowledgeMatch document,
    OutfitColorVariant variant,
    List<UUID> matchedClothesIds
) {

    /**
     * 문서와 색 배치의 존재를 확인하고 일치한 ID 목록을 복사한다.
     */
    public OutfitColorEvidence {
        Objects.requireNonNull(document, "근거 문서는 필수입니다.");
        Objects.requireNonNull(variant, "근거 색 배치는 필수입니다.");
        matchedClothesIds = List.copyOf(matchedClothesIds);
    }

    /**
     * 매칭 과정에서 선택한 문서와 색 배치를 최종 코디의 일치 의상 ID와 함께 보관한다.
     */
    public static OutfitColorEvidence of(StyleKnowledgeMatch document, OutfitColorVariant variant,
        List<UUID> matchedClothesIds) {
        return new OutfitColorEvidence(document, variant, matchedClothesIds);
    }
}
