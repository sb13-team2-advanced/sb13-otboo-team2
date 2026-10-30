package codeit.ootd.domain.recommendation.dto.response;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.enums.ClothesType;

import java.util.List;
import java.util.UUID;

/**
 * 추천 결과에 포함된 개별 의상의 기본 정보와 속성을 전달하는 응답 DTO.
 *
 * @param clothesId 추천 의상 ID
 * @param name 의상 이름
 * @param imageUrl 의상 이미지 URL. 등록된 이미지가 없으면 null
 * @param type 상의, 하의, 아우터 등 의상 종류
 * @param attributes 속성 정의와 선택한 값을 포함한 의상 속성 목록
 */
public record RecommendationClothesResponse(UUID clothesId, String name, String imageUrl,
                                            ClothesType type, List<ClothesAttributeWithDefDto> attributes) {
}
