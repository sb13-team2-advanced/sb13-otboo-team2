package codeit.ootd.domain.clothes.extraction;

import codeit.ootd.domain.clothes.enums.ClothesType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 상품 제목의 키워드로 의상 종류를 정한다. 여러 종류의 키워드가 섞이면 앞선 종류가 이긴다.
 * 예를 들어 "데님 자켓"은 데님(하의)보다 자켓(아우터)이 앞선다.
 *
 * <p>키워드가 다른 단어의 일부로 걸리는 경우는 두 가지로 바로잡는다.
 * <ul>
 *   <li>예외 문구: "부츠컷"처럼 정해 둔 문구가 제목에 있으면 키워드 매칭 전에 종류를 바로 정한다.</li>
 *   <li>무시 단어: "브라운"처럼 키워드("브라")와 겹치는 무관한 단어는 지우고 키워드를 찾는다.</li>
 * </ul>
 */
@Component
public class ClothesTypeResolver {

    private static final Map<ClothesType, List<String>> KEYWORDS = new LinkedHashMap<>();

    private static final Map<String, ClothesType> PHRASE_OVERRIDES = new LinkedHashMap<>();

    private static final List<String> IGNORED_WORDS = List.of("브라운", "캡슐", "baggy", "베스트셀러");

    static {
        KEYWORDS.put(ClothesType.SOCKS, List.of("양말", "socks"));
        KEYWORDS.put(ClothesType.SHOES,
                List.of("신발", "운동화", "스니커즈", "러닝화", "부츠", "로퍼", "샌들", "슬리퍼",
                        "구두", "슈즈", "뮬", "워커", "sneakers", "boots", "shoes"));
        KEYWORDS.put(ClothesType.HAT, List.of("모자", "캡", "비니", "햇", "버킷", "cap", "beanie", "hat"));
        KEYWORDS.put(ClothesType.BAG,
                List.of("가방", "백팩", "크로스백", "토트백", "숄더백", "에코백", "핸드백", "클러치", "bag"));
        KEYWORDS.put(ClothesType.SCARF, List.of("스카프", "머플러", "목도리", "넥워머"));
        KEYWORDS.put(ClothesType.UNDERWEAR, List.of("속옷", "팬티", "브라", "드로즈", "underwear"));
        KEYWORDS.put(ClothesType.DRESS, List.of("원피스", "드레스", "dress"));
        KEYWORDS.put(ClothesType.OUTER,
                List.of("자켓", "재킷", "코트", "점퍼", "패딩", "가디건", "집업", "블레이저", "무스탕",
                        "조끼", "블루종", "카디건", "트렌치", "바람막이", "후리스", "플리스", "아노락",
                        "jacket", "coat", "parka", "cardigan", "windbreaker", "fleece"));
        KEYWORDS.put(ClothesType.BOTTOM,
                List.of("바지", "팬츠", "슬랙스", "데님", "치마", "스커트", "레깅스",
                        "쇼츠", "숏츠", "조거", "카고", "pants", "jeans", "skirt", "shorts"));
        KEYWORDS.put(ClothesType.TOP,
                List.of("티셔츠", "셔츠", "맨투맨", "후드", "니트", "블라우스", "스웨터",
                        "반팔", "긴팔", "민소매", "나시", "카라티", "폴로", "t-shirt", "shirt", "hoodie", "sweater"));
        KEYWORDS.put(ClothesType.ACCESSORY,
                List.of("벨트", "시계", "목걸이", "반지", "귀걸이", "팔찌", "선글라스",
                        "장갑", "넥타이", "안경", "belt", "watch"));

        PHRASE_OVERRIDES.put("부츠컷", ClothesType.BOTTOM);
        PHRASE_OVERRIDES.put("버킷백", ClothesType.BAG);
        PHRASE_OVERRIDES.put("dress shirt", ClothesType.TOP);
        PHRASE_OVERRIDES.put("드레스 셔츠", ClothesType.TOP);
        PHRASE_OVERRIDES.put("드레스셔츠", ClothesType.TOP);
    }

    public ClothesType resolve(String text) {
        if (text == null || text.isBlank()) {
            return ClothesType.ETC;
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ClothesType> override : PHRASE_OVERRIDES.entrySet()) {
            if (lowered.contains(override.getKey())) {
                return override.getValue();
            }
        }
        String cleaned = removeIgnoredWords(lowered);
        return KEYWORDS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(cleaned::contains))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(ClothesType.ETC);
    }

    private String removeIgnoredWords(String text) {
        String result = text;
        for (String word : IGNORED_WORDS) {
            result = result.replace(word, " ");
        }
        return result;
    }
}
