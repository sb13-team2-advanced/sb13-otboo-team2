package codeit.ootd.domain.clothes.service;

import codeit.ootd.domain.clothes.dto.response.ClothesDto;

public interface ClothesExtractionService {

    /**
     * 구매 링크 페이지에서 의상 정보를 읽어 온다. 저장하지 않고 등록 폼을 채울 값만 돌려준다.
     */
    ClothesDto extract(String url);
}
