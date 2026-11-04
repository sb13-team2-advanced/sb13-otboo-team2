package codeit.ootd.domain.recommendation.service;

import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;

import java.util.UUID;

public interface RecommendationService {

    /**
     * 기온에 맞는 후보 중 랜덤으로 의상 조합을 추천한다.
     */
    RecommendationResponse recommend(UUID userId, UUID weatherId);
}
