package codeit.ootd.domain.recommendation.service;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.algorithm.ClothesColorResolver;
import codeit.ootd.domain.recommendation.algorithm.OutfitColorMatcher;
import codeit.ootd.domain.recommendation.algorithm.OutfitRecommender;
import codeit.ootd.domain.recommendation.algorithm.RecommendationConditions;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * 기존 날씨 추천의 기준 의상으로 한 번 검색하고 검색한 배색을 같은 날씨 적합도의 의상 선택에 반영한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutfitRecommendationService {

    private static final List<ClothesType> ANCHOR_PRIORITY =
        List.of(ClothesType.BOTTOM, ClothesType.TOP, ClothesType.OUTER);

    private final OutfitRecommender weatherRecommender;
    private final ClothesColorResolver colorResolver;
    private final StyleKnowledgeSearchService knowledgeSearchService;
    private final OutfitColorMatcher colorMatcher;

    /**
     * 상하의 코디는 하의·상의·아우터 순서로 색상을 아는 기준 의상을 선택하고 배색 유형을 제한하지 않는다.
     * 원피스는 대응하는 문서가 없으므로 기존 추천을 유지한다.
     * 외부 HTTP 호출 때 상위 트랜잭션을 중지하고 지식 조회는 Repository의 별도 트랜잭션에서 실행한다.
     * 임베딩 통신·응답 검증 또는 지식 DB 조회가 실패하면 기존 날씨 추천으로 대체한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<RecommendationClothesResponse> recommend(RecommendationConditions conditions,
        List<RecommendationClothesResponse> clothes) {

        List<RecommendationClothesResponse> baseline = weatherRecommender.recommend(conditions, clothes);

        if (baseline.stream().anyMatch(item -> item.type() == ClothesType.DRESS)) {
            return baseline;
        }

        Optional<RecommendationClothesResponse> anchor = ANCHOR_PRIORITY.stream()
            .flatMap(type -> baseline.stream().filter(item -> item.type() == type))
            .filter(item -> colorResolver.resolve(item).isPresent())
            .findFirst();

        if (anchor.isEmpty()) {
            return baseline;
        }

        RecommendationClothesResponse reference = anchor.get();
        String baseColor = colorResolver.resolve(reference).orElseThrow();
        List<StyleKnowledgeMatch> documents;

        try {
            documents = knowledgeSearchService.search(query(reference.type(), baseColor), baseColor, null);
        } catch (RestClientException | IllegalStateException | DataAccessException exception) {
            log.warn("배색 검색에 실패해 날씨 추천을 사용합니다. cause={}", exception.getClass().getSimpleName());
            return baseline;
        }

        return colorMatcher.match(conditions, clothes, baseline, reference, documents);
    }

    /**
     * 사용자가 검색어를 입력하지 않아도 서버가 기준 의상과 색상으로 검색 문장을 구성한다.
     */
    private String query(ClothesType type, String color) {
        String label = switch (type) {
            case TOP -> "상의";
            case BOTTOM -> "하의";
            case OUTER -> "아우터";
            default -> throw new IllegalArgumentException("배색 검색을 지원하지 않는 기준 의상입니다.");
        };

        return "%s %s를 기준으로 상의, 하의, 아우터와 어울리는 색 조합".formatted(color, label);
    }
}
