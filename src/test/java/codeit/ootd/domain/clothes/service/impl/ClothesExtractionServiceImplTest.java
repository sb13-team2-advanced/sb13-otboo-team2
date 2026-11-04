package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.extraction.ClothesPageParser;
import codeit.ootd.domain.clothes.extraction.ClothesTypeResolver;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import codeit.ootd.infra.web.WebPageFetcher;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClothesExtractionServiceImplTest {

    private static final String URL = "https://shop.example/p/1";

    @Mock
    private WebPageFetcher pageFetcher;
    @Mock
    private ClothesAttributeDefinitionRepository definitionRepository;

    private ClothesExtractionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClothesExtractionServiceImpl(pageFetcher, new ClothesPageParser(), new ClothesTypeResolver(),
                definitionRepository);
    }

    @Test
    void 제목_이미지_종류를_채우고_저장용_값은_비워서_돌려준다() {
        given(pageFetcher.fetch(URL)).willReturn(html("오버핏 블랙 자켓", "https://cdn.shop.example/a.jpg"));
        given(definitionRepository.findAll()).willReturn(List.of());

        ClothesDto dto = service.extract(URL);

        assertThat(dto.id()).isNull();
        assertThat(dto.ownerId()).isNull();
        assertThat(dto.name()).isEqualTo("오버핏 블랙 자켓");
        assertThat(dto.imageUrl()).isEqualTo("https://cdn.shop.example/a.jpg");
        assertThat(dto.type()).isEqualTo(ClothesType.OUTER);
    }

    @Test
    void 제목에_선택값이_들어_있는_속성만_채운다() {
        UUID colorId = UUID.randomUUID();
        ClothesAttributeDefinition color = definition(colorId, "색상", List.of("화이트", "블랙"));
        ClothesAttributeDefinition material = definition(UUID.randomUUID(), "소재", List.of("면", "울"));
        given(pageFetcher.fetch(URL)).willReturn(html("오버핏 블랙 자켓", null));
        given(definitionRepository.findAll()).willReturn(List.of(color, material));

        ClothesDto dto = service.extract(URL);

        assertThat(dto.attributes()).containsExactly(
                new ClothesAttributeWithDefDto(colorId, "색상", List.of("화이트", "블랙"), "블랙"));
    }

    @Test
    void 영문_선택값은_대소문자를_구분하지_않고_맞춘다() {
        UUID colorId = UUID.randomUUID();
        given(pageFetcher.fetch(URL)).willReturn(html("Oversized BLACK Jacket", null));
        given(definitionRepository.findAll())
                .willReturn(List.of(definition(colorId, "색상", List.of("white", "black"))));

        ClothesDto dto = service.extract(URL);

        assertThat(dto.attributes()).extracting(ClothesAttributeWithDefDto::value).containsExactly("black");
    }

    @Test
    void 맞는_속성이_없으면_빈_목록을_돌려준다() {
        given(pageFetcher.fetch(URL)).willReturn(html("알 수 없는 상품", null));
        given(definitionRepository.findAll())
                .willReturn(List.of(definition(UUID.randomUUID(), "색상", List.of("블랙"))));

        ClothesDto dto = service.extract(URL);

        assertThat(dto.attributes()).isNotNull().isEmpty();
        assertThat(dto.type()).isEqualTo(ClothesType.ETC);
        assertThat(dto.imageUrl()).isNull();
    }

    @Test
    void 제목이_50자를_넘으면_등록_가능한_길이로_자른다() {
        String longTitle = "자켓".repeat(40);
        given(pageFetcher.fetch(URL)).willReturn(html(longTitle, null));
        given(definitionRepository.findAll()).willReturn(List.of());

        ClothesDto dto = service.extract(URL);

        assertThat(dto.name()).hasSize(50).isEqualTo(longTitle.substring(0, 50));
    }

    @Test
    void 페이지에서_제목을_찾지_못하면_예외가_난다() {
        given(pageFetcher.fetch(URL)).willReturn("<html><body>내용 없음</body></html>");

        assertThatThrownBy(() -> service.extract(URL))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_NOT_FOUND));
        verifyNoInteractions(definitionRepository);
    }

    @Test
    void 페이지를_가져오지_못하면_그_예외를_그대로_전달한다() {
        given(pageFetcher.fetch(URL)).willThrow(new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED));

        assertThatThrownBy(() -> service.extract(URL))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED));
        verifyNoInteractions(definitionRepository);
    }

    private static String html(String title, String imageUrl) {
        String image = imageUrl == null ? "" : "<meta property=\"og:image\" content=\"" + imageUrl + "\">";
        return "<html><head><meta property=\"og:title\" content=\"" + title + "\">" + image + "</head></html>";
    }

    private static ClothesAttributeDefinition definition(UUID id, String name, List<String> values) {
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition(name, values);
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }
}
