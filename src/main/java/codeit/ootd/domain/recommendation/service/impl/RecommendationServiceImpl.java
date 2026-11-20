package codeit.ootd.domain.recommendation.service.impl;

import codeit.ootd.domain.recommendation.dto.RecommendationContext;
import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;
import codeit.ootd.domain.recommendation.service.OutfitRecommendationService;
import codeit.ootd.domain.recommendation.service.RecommendationContextLoader;
import codeit.ootd.domain.recommendation.service.RecommendationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final RecommendationContextLoader contextLoader;
    private final OutfitRecommendationService recommender;

    /**
     * 본인 의상과 날씨를 트랜잭션 안에서 조회한 뒤 배색 검색과 의상 선택을 수행한다.
     * 남은 예보나 본인 의상이 없으면 외부 검색 없이 빈 추천을 반환한다.
     * 추천 조건이 없으면 옷장 비어 있음과 남은 예보 없음을 구분해 기록한다.
     */
    @Override
    public RecommendationResponse recommend(UUID userId, UUID weatherId) {

        RecommendationContext context = contextLoader.load(userId, weatherId);

        return new RecommendationResponse(weatherId, userId,
            context.conditions()
                .map(conditions -> recommender.recommend(conditions, context.candidates()))
                .orElseGet(() -> {
                    log.debug("의상 추천 생략. applied=false reason={}",
                        context.candidates().isEmpty() ? "EMPTY_WARDROBE" : "NO_REMAINING_FORECAST");
                    return List.of();
                }));
    }
}
