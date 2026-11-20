package codeit.ootd.domain.recommendation.dto.response;

import codeit.ootd.domain.recommendation.dto.OutfitColorEvidence;
import java.util.List;
import java.util.UUID;

/**
 * 서버가 확인한 배색 문서의 출처와 최종 추천에서 색상이 일치한 의상만 전달한다.
 */
public record RecommendationSource(String documentKey, String title, String url, List<UUID> matchedClothesIds) {

    /**
     * 출처에 연결된 의상 ID 목록을 복사한다.
     */
    public RecommendationSource {
        matchedClothesIds = List.copyOf(matchedClothesIds);
    }

    /**
     * LLM이 만든 출처를 사용하지 않고 서버가 선택한 문서와 일치 의상으로 생성한다.
     */
    public static RecommendationSource from(OutfitColorEvidence evidence) {
        return new RecommendationSource(evidence.document().documentKey(), evidence.document().title(),
            evidence.document().sourceUrl(), evidence.matchedClothesIds());
    }
}
