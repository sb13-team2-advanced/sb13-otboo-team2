package codeit.ootd.domain.recommendation.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * 추천 조회 API에서 날씨와 사용자에 맞춰 선택한 의상 목록을 반환하는 응답 DTO.
 *
 * @param weatherId 추천 기준으로 사용한 날씨 ID
 * @param userId 추천 대상 사용자 ID
 * @param clothes 추천 의상 목록. 옷장이 비었거나 코디를 구성할 수 없으면 빈 목록
 */
public record RecommendationResponse(UUID weatherId, UUID userId,
                                     List<RecommendationClothesResponse> clothes) {
}
