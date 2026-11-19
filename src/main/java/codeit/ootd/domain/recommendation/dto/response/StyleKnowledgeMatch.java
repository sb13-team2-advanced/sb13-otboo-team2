package codeit.ootd.domain.recommendation.dto.response;

import codeit.ootd.domain.recommendation.enums.CombinationType;

/**
 * 검색한 코디 문서와 출처, 의상별 색 배치를 담은 JSON 및 코사인 유사도를 전달한다.
 * 유사도는 정답 확률이 아니며 metadataJson은 JSONB 메타데이터를 문자열로 변환한 값이다.
 */
public record StyleKnowledgeMatch(
    String documentKey,
    String title,
    String content,
    String baseColor,
    CombinationType combinationType,
    String sourceType,
    String sourceUrl,
    String metadataJson,
    double similarity
) {
}
