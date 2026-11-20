package codeit.ootd.domain.clothes.service;

import java.util.UUID;

public interface ClothesExtractionRateLimiter {

    /**
     * 사용자가 구매 링크 불러오기를 호출해도 되는지 확인한다. 한도를 넘으면 429 예외를 던진다.
     */
    void check(UUID userId);
}
