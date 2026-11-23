package codeit.ootd.domain.recommendation.algorithm;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 등록된 색상 속성을 배색 문서와 비교할 수 있는 이름으로 변환한다.
 * 명시한 동의어만 통일하고 서로 다른 색조나 복합 색상을 임의로 합치지 않는다.
 */
@Component
public class ClothesColorResolver {

    private static final String COLOR_ATTRIBUTE = "색상";
    private static final int SINGLE_COLOR_COUNT = 1;
    private static final int CONFLICT_CHECK_LIMIT = 2;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> CANONICAL_COLORS = Set.of(
        "그레이", "그린", "네이비", "레드", "베이지", "브라운", "블랙", "블루",
        "옐로", "오렌지", "퍼플", "핑크", "화이트",
        "데님", "라벤더", "라이트 블루", "라일락", "머스터드", "버건디", "블러시 핑크",
        "블루 그레이", "선명한 레드", "세이지", "소프트 화이트", "스카이 블루", "스톤",
        "실버", "아이보리", "에메랄드", "올리브", "적갈색", "차콜", "초콜릿", "카멜",
        "코랄", "코발트 블루", "크림", "탠", "터쿼이즈", "토프", "틸", "포레스트 그린",
        "푸크시아", "플럼"
    );
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("검정", "블랙"), Map.entry("검정색", "블랙"), Map.entry("검은색", "블랙"),
        Map.entry("흰색", "화이트"), Map.entry("하양", "화이트"), Map.entry("하얀색", "화이트"),
        Map.entry("회색", "그레이"), Map.entry("남색", "네이비"),
        Map.entry("파랑", "블루"), Map.entry("파란색", "블루"),
        Map.entry("초록", "그린"), Map.entry("초록색", "그린"), Map.entry("녹색", "그린"),
        Map.entry("빨강", "레드"), Map.entry("빨간색", "레드"),
        Map.entry("갈색", "브라운"), Map.entry("분홍", "핑크"), Map.entry("분홍색", "핑크"),
        Map.entry("보라", "퍼플"), Map.entry("보라색", "퍼플"),
        Map.entry("주황", "오렌지"), Map.entry("주황색", "오렌지"),
        Map.entry("노랑", "옐로"), Map.entry("노란색", "옐로"), Map.entry("옐로우", "옐로"),
        Map.entry("black", "블랙"), Map.entry("white", "화이트"),
        Map.entry("gray", "그레이"), Map.entry("grey", "그레이"), Map.entry("navy", "네이비"),
        Map.entry("blue", "블루"), Map.entry("green", "그린"), Map.entry("red", "레드"),
        Map.entry("beige", "베이지"), Map.entry("brown", "브라운"), Map.entry("pink", "핑크"),
        Map.entry("purple", "퍼플"), Map.entry("orange", "오렌지"), Map.entry("yellow", "옐로")
    );

    /**
     * 색상 속성만 읽고 정규화한 값이 하나일 때 반환한다.
     * 속성 누락, 미지원 값 또는 서로 다른 색상 속성이 있으면 판단을 보류한다.
     * 반환값이 비어 있으면 호출 측에서 기존 날씨 기반 추천을 사용할 수 있다.
     */
    public Optional<String> resolve(RecommendationClothesResponse clothes) {
        if (clothes.attributes() == null) {
            return Optional.empty();
        }

        List<Optional<String>> colors = clothes.attributes().stream()
            .filter(attribute -> attribute != null && attribute.definitionName() != null)
            .filter(attribute -> COLOR_ATTRIBUTE.equals(attribute.definitionName().strip()))
            .map(ClothesAttributeWithDefDto::value)
            .map(this::normalize)
            .distinct()
            .limit(CONFLICT_CHECK_LIMIT)
            .toList();

        return colors.size() == SINGLE_COLOR_COUNT ? colors.get(0) : Optional.empty();
    }

    /**
     * 공백과 영문 대소문자를 정리한 뒤 배색 문서의 색상명 또는 명시된 동의어를 반환한다.
     * 지원하지 않는 표현과 여러 색을 합친 값은 빈 결과로 반환한다.
     */
    public Optional<String> normalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = WHITESPACE.matcher(value.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
        if (CANONICAL_COLORS.contains(normalized)) {
            return Optional.of(normalized);
        }

        return Optional.ofNullable(ALIASES.get(normalized));
    }
}
