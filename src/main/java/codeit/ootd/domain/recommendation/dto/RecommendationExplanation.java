package codeit.ootd.domain.recommendation.dto;

import codeit.ootd.domain.recommendation.dto.response.RecommendationSource;
import java.util.List;

/**
 * 생성 또는 기본 설명과 서버에서 검증한 출처를 함께 전달한다.
 */
public record RecommendationExplanation(String text, List<RecommendationSource> sources) {

    /**
     * 설명에 사용한 출처 목록을 복사한다.
     */
    public RecommendationExplanation {
        sources = List.copyOf(sources);
    }

    /**
     * 설명 문자열과 서버가 확인한 출처로 결과를 생성한다.
     */
    public static RecommendationExplanation of(String text, List<RecommendationSource> sources) {
        return new RecommendationExplanation(text, sources);
    }
}
