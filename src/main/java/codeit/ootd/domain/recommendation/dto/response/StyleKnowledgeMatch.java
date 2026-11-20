package codeit.ootd.domain.recommendation.dto.response;

import codeit.ootd.domain.recommendation.enums.CombinationType;
import java.util.List;

/**
 * 검색한 코디 문서와 출처, 검증한 의상별 색 배치 및 코사인 유사도를 전달한다.
 * 유사도는 정답 확률이 아니며 색 배치가 없으면 해당 문서를 색상 비교에 사용할 수 없다.
 */
public record StyleKnowledgeMatch(
    String documentKey,
    String title,
    String content,
    String baseColor,
    CombinationType combinationType,
    String sourceType,
    String sourceUrl,
    List<OutfitColorVariant> outfitVariants,
    double similarity
) {

    /**
     * 색 배치 목록을 복사해 검색 결과가 생성된 뒤 외부에서 변경되지 않게 한다.
     */
    public StyleKnowledgeMatch {
        outfitVariants = List.copyOf(outfitVariants);
    }
}
