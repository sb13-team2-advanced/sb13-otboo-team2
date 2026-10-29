package codeit.ootd.domain.recommendation.controller;

import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;
import codeit.ootd.domain.recommendation.service.RecommendationService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

import java.security.Principal;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 Principal 이름의 사용자 UUID로 본인의 의상만 추천한다.
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 동일 날씨로 재요청해도 적합한 후보 중 랜덤으로 추천한다.
     */
    @GetMapping
    public RecommendationResponse recommend(@RequestParam UUID weatherId, Principal principal) {

        if (principal == null) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        UUID userId;
        try {
            userId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        return recommendationService.recommend(userId, weatherId);
    }
}
