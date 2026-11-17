package codeit.ootd.domain.clothes.extraction;

import codeit.ootd.domain.clothes.enums.ClothesType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 상품 제목의 키워드로 의상 종류를 정한다. 여러 종류의 키워드가 섞이면 앞선 종류가 이긴다.
 * 예를 들어 "데님 자켓"은 데님(하의)보다 자켓(아우터)이 앞선다.
 */
@Component
public class ClothesTypeResolver {

    private static final Map<ClothesType, List<String>> KEYWORDS = new java.util.LinkedHashMap<>();

    static {
        KEYWORDS.put(ClothesType.SOCKS, List.of("양말", "socks"));
        KEYWORDS.put(ClothesType.SHOES,
                List.of("신발", "운동화", "스니커즈", "러닝화", "부츠", "로퍼", "샌들", "슬리퍼", "sneakers", "boots", "shoes"));
        KEYWORDS.put(ClothesType.HAT, List.of("모자", "캡", "비니", "햇", "버킷", "cap", "beanie", "hat"));
        KEYWORDS.put(ClothesType.BAG, List.of("가방", "백팩", "크로스백", "토트백", "bag"));
        KEYWORDS.put(ClothesType.SCARF, List.of("스카프", "머플러", "목도리"));
        KEYWORDS.put(ClothesType.UNDERWEAR, List.of("속옷", "팬티", "브라", "드로즈", "underwear"));
        KEYWORDS.put(ClothesType.DRESS, List.of("원피스", "드레스", "dress"));
        KEYWORDS.put(ClothesType.OUTER,
                List.of("자켓", "재킷", "코트", "점퍼", "패딩", "가디건", "집업", "블레이저", "무스탕", "jacket", "coat", "parka"));
        KEYWORDS.put(ClothesType.BOTTOM,
                List.of("바지", "팬츠", "슬랙스", "데님", "치마", "스커트", "레깅스", "pants", "jeans", "skirt", "shorts"));
        KEYWORDS.put(ClothesType.TOP,
                List.of("티셔츠", "셔츠", "맨투맨", "후드", "니트", "블라우스", "스웨터", "t-shirt", "shirt", "hoodie", "sweater"));
        KEYWORDS.put(ClothesType.ACCESSORY,
                List.of("벨트", "시계", "목걸이", "반지", "귀걸이", "팔찌", "선글라스", "belt", "watch"));
    }

    public ClothesType resolve(String text) {
        if (text == null || text.isBlank()) {
            return ClothesType.ETC;
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        return KEYWORDS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(lowered::contains))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(ClothesType.ETC);
    }
}
