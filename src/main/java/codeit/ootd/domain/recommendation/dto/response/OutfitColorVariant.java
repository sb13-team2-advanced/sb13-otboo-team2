package codeit.ootd.domain.recommendation.dto.response;

import codeit.ootd.domain.clothes.enums.ClothesType;

/**
 * 기준 의상 종류와 상의·하의·아우터·소품의 정규화한 색 배치를 전달한다.
 * 소품 색상은 개별 신발이나 가방의 종류를 지정하지 않은 문서의 공통 소품 색상이다.
 */
public record OutfitColorVariant(
    ClothesType selectedType,
    String topColor,
    String bottomColor,
    String outerColor,
    String accessoryColor
) {

    /**
     * 메타데이터 파서가 검증한 기준 종류와 색상으로 색 배치 객체를 생성한다.
     */
    public static OutfitColorVariant of(ClothesType selectedType, String topColor,
        String bottomColor, String outerColor, String accessoryColor) {
        return new OutfitColorVariant(selectedType, topColor, bottomColor, outerColor, accessoryColor);
    }
}
