package codeit.ootd.infra.groq;

import java.util.List;

/**
 * Groq가 생성한 설명과 스스로 표시한 근거를 전달하며, 호출 서비스에서 실제 검색 근거와 비교한다.
 */
public record GroqExplanation(String explanation, String documentKey, List<String> matchedClothesIds) {
}
