package codeit.ootd.domain.recommendation.controller;

import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;
import codeit.ootd.domain.recommendation.service.RecommendationService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWT 인증 과정에서 사용자와 세션을 검증한 UUID로 본인의 의상만 추천한다.
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 인증된 사용자 UUID를 전달해 동일 날씨의 적합한 후보 중 랜덤으로 추천한다.
     */
    @GetMapping
    public RecommendationResponse recommend(@RequestParam UUID weatherId,
                                            @AuthenticationPrincipal UUID userId) {

        if (userId == null) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        return recommendationService.recommend(userId, weatherId);
    }
}
