package codeit.ootd.domain.recommendation.service;

import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;

import java.util.UUID;

public interface RecommendationService {

    RecommendationResponse recommend(UUID userId, UUID weatherId);
}
