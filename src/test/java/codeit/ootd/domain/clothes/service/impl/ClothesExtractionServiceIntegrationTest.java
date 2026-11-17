package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.service.ClothesExtractionService;
import codeit.ootd.infra.web.FetchedPage;
import codeit.ootd.infra.web.WebPageFetcher;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 테스트 자체에는 트랜잭션을 걸지 않는다. 실제 요청처럼 서비스가 트랜잭션 밖에서 호출될 때
 * 지연 로딩되는 선택값을 읽을 수 있는지 확인한다(open-in-view=false).
 */
@SpringBootTest
@ActiveProfiles("test")
class ClothesExtractionServiceIntegrationTest {

    private static final String URL = "https://shop.example/p/1";
    private static final String DEFINITION_NAME = "추출테스트-색상";

    @Autowired
    private ClothesExtractionService extractionService;
    @Autowired
    private ClothesAttributeDefinitionRepository definitionRepository;

    @MockitoBean
    private WebPageFetcher pageFetcher;

    @AfterEach
    void tearDown() {
        definitionRepository.findAll().stream()
                .filter(definition -> DEFINITION_NAME.equals(definition.getName()))
                .forEach(definitionRepository::delete);
    }

    @Test
    void 저장된_속성_정의의_선택값을_트랜잭션_밖에서도_읽어_속성을_채운다() {
        ClothesAttributeDefinition saved = definitionRepository.saveAndFlush(
                new ClothesAttributeDefinition(DEFINITION_NAME, List.of("화이트", "블랙")));
        String html = "<html><head><meta property=\"og:title\" content=\"오버핏 블랙 자켓\"></head></html>";
        given(pageFetcher.fetch(URL)).willReturn(new FetchedPage(html, URL));

        ClothesDto dto = extractionService.extract(URL);

        assertThat(dto.attributes()).contains(
                new ClothesAttributeWithDefDto(saved.getId(), DEFINITION_NAME, List.of("화이트", "블랙"), "블랙"));
    }
}
