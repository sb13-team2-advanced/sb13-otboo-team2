package codeit.ootd.domain.recommendation.dto.response;

import java.util.List;
import java.util.UUID;
import codeit.ootd.domain.recommendation.dto.RecommendationExplanation;

/**
 * 추천 조회 API에서 날씨와 사용자에 맞춰 선택한 의상 목록을 반환하는 응답 DTO.
 *
 * @param weatherId 추천 기준으로 사용한 날씨 ID
 * @param userId 추천 대상 사용자 ID
 * @param clothes 추천 의상 목록. 옷장이 비었거나 코디를 구성할 수 없으면 빈 목록
 * @param explanation 검색 근거로 생성한 설명 또는 기본 설명
 * @param sources 서버가 검증한 설명의 배색 출처
 */
public record RecommendationResponse(UUID weatherId, UUID userId,
    List<RecommendationClothesResponse> clothes, String explanation, List<RecommendationSource> sources) {

    /**
     * 의상과 출처 목록을 복사한다.
     */
    public RecommendationResponse {
        clothes = List.copyOf(clothes);
        sources = List.copyOf(sources);
    }

    /**
     * 추천 결과와 생성 또는 기본 설명을 기존 의상 응답에 결합한다.
     */
    public static RecommendationResponse of(UUID weatherId, UUID userId,
        List<RecommendationClothesResponse> clothes, RecommendationExplanation explanation) {
        return new RecommendationResponse(weatherId, userId, clothes, explanation.text(), explanation.sources());
    }
}
