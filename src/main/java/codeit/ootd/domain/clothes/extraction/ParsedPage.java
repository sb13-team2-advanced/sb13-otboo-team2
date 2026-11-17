package codeit.ootd.domain.clothes.extraction;

/**
 * 구매 링크 페이지에서 읽어 낸 제목과 대표 이미지 주소. 값이 없으면 null이다.
 */
public record ParsedPage(String title, String imageUrl) {
}
