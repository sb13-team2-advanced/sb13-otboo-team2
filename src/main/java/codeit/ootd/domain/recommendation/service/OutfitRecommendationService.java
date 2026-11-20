package codeit.ootd.domain.recommendation.service;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.algorithm.ClothesColorResolver;
import codeit.ootd.domain.recommendation.algorithm.OutfitColorMatcher;
import codeit.ootd.domain.recommendation.algorithm.OutfitRecommender;
import codeit.ootd.domain.recommendation.algorithm.RecommendationConditions;
import codeit.ootd.domain.recommendation.dto.OutfitColorEvidence;
import codeit.ootd.domain.recommendation.dto.OutfitColorMatchResult;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
     * 날씨와 배색으로 선택한 의상 목록을 반환한다.
     */
    public List<RecommendationClothesResponse> recommend(RecommendationConditions conditions,
        List<RecommendationClothesResponse> clothes) {
        return recommendWithEvidence(conditions, clothes).clothes();
    }

    /**
     * 상하의 코디는 하의·상의·아우터 순서로 색상을 아는 기준 의상을 선택하고 배색 유형을 제한하지 않는다.
     * 원피스는 대응하는 문서가 없으므로 기존 추천을 유지한다.
     * 외부 HTTP 호출 때 상위 트랜잭션을 중지하고 지식 조회는 Repository의 별도 트랜잭션에서 실행한다.
     * 임베딩 통신·응답 검증 또는 지식 DB 조회가 실패하면 기존 날씨 추천으로 대체한다.
     * 기준 의상과 검색 문서, 선택 결과 및 대체 이유를 기록하되 문서 본문과 임베딩은 출력하지 않는다.
     */

    public OutfitColorMatchResult recommendWithEvidence(RecommendationConditions conditions,
        List<RecommendationClothesResponse> clothes) {

        List<RecommendationClothesResponse> baseline = weatherRecommender.recommend(conditions, clothes);

        if (baseline.isEmpty()) {
            log.debug("배색 추천 생략. applied=false reason=NO_COMPLETE_OUTFIT");
            return OutfitColorMatchResult.withoutEvidence(baseline);
        }

        if (baseline.stream().anyMatch(item -> item.type() == ClothesType.DRESS)) {
            log.debug("배색 추천 생략. applied=false reason=UNSUPPORTED_OUTFIT_TYPE outfitType=DRESS");
            return OutfitColorMatchResult.withoutEvidence(baseline);
        }

        Optional<RecommendationClothesResponse> anchor = ANCHOR_PRIORITY.stream()
            .flatMap(type -> baseline.stream().filter(item -> item.type() == type))
            .filter(item -> colorResolver.resolve(item).isPresent())
            .findFirst();

        if (anchor.isEmpty()) {
            log.debug("배색 추천 생략. applied=false reason=NO_KNOWN_ANCHOR_COLOR");
            return OutfitColorMatchResult.withoutEvidence(baseline);
        }

        RecommendationClothesResponse reference = anchor.get();
        String baseColor = colorResolver.resolve(reference).orElseThrow();
        List<StyleKnowledgeMatch> documents;

        log.debug("배색 검색 시작. anchorType={} anchorClothesId={} baseColor={}",
            reference.type(), reference.clothesId(), baseColor);

        try {
            documents = knowledgeSearchService.search(query(reference.type(), baseColor), baseColor, null);
        } catch (RestClientException | IllegalStateException | DataAccessException exception) {

            String reason = exception instanceof DataAccessException ? "KNOWLEDGE_QUERY_FAILED"
                : exception instanceof RestClientException ? "EMBEDDING_REQUEST_FAILED"
                : "INVALID_EMBEDDING_RESPONSE";

            log.warn("배색 검색에 실패해 날씨 추천을 사용합니다. applied=false reason={} "
                    + "anchorType={} anchorClothesId={} baseColor={} cause={}",
                reason, reference.type(), reference.clothesId(), baseColor, exception.getClass().getSimpleName());

            return OutfitColorMatchResult.withoutEvidence(baseline);
        }

        OutfitColorMatchResult result =
            colorMatcher.match(conditions, clothes, baseline, reference, documents);

        logResult(reference, baseColor, documents, baseline, result);

        return result;
    }

    /**
     * DEBUG가 켜져 있을 때만 ID 목록과 교체 내역을 계산하고 매칭 결과의 변경 여부를 기록한다.
     * 검색 문서 없음, 유효한 색 배치 없음, 개선 없음, 실제 교체를 구분하여 최종 결과 한 줄에 기록한다.
     * 검색된 전체 문서 키와 최종 코디의 근거 문서 키를 구분하고 일치한 의상 ID를 함께 기록한다.
     */
    private void logResult(RecommendationClothesResponse anchor, String baseColor,
        List<StyleKnowledgeMatch> documents, List<RecommendationClothesResponse> baseline,
        OutfitColorMatchResult matchResult) {

        if (!log.isDebugEnabled()) {
            return;
        }

        List<RecommendationClothesResponse> result = matchResult.clothes();

        List<UUID> beforeIds = baseline.stream().map(RecommendationClothesResponse::clothesId).toList();

        List<UUID> afterIds = result.stream().map(RecommendationClothesResponse::clothesId).toList();

        boolean applied = matchResult.changed();

        String reason = documents.isEmpty() ? "NO_KNOWLEDGE_DOCUMENTS"
            : documents.stream().allMatch(document -> document.outfitVariants().isEmpty()) ? "NO_VALID_COLOR_VARIANTS"
            : applied ? "COLOR_MATCH_IMPROVED" : "NO_COLOR_IMPROVEMENT";

        Map<ClothesType, UUID> previousIds = baseline.stream()
            .collect(Collectors.toMap(RecommendationClothesResponse::type, RecommendationClothesResponse::clothesId));

        List<String> changes = result.stream()
            .filter(item -> !item.clothesId().equals(previousIds.get(item.type())))
            .map(item -> "%s:%s->%s".formatted(item.type(), previousIds.get(item.type()), item.clothesId()))
            .toList();

        log.debug("배색 추천 결과. anchorType={} anchorClothesId={} baseColor={} documentKeys={} "
                + "applied={} reason={} beforeIds={} afterIds={} changes={} "
                + "evidenceDocumentKey={} evidenceSelectedType={} matchedClothesIds={}",
            anchor.type(), anchor.clothesId(), baseColor,
            documents.stream().map(StyleKnowledgeMatch::documentKey).toList(),
            applied, reason, beforeIds, afterIds, changes,
            matchResult.evidence().map(evidence -> evidence.document().documentKey()).orElse(null),
            matchResult.evidence().map(evidence -> evidence.variant().selectedType()).orElse(null),
            matchResult.evidence().map(OutfitColorEvidence::matchedClothesIds).orElseGet(List::of));
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
