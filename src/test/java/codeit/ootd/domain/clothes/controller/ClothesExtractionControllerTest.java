package codeit.ootd.domain.clothes.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.service.ClothesExtractionService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import codeit.ootd.global.exception.OtbooException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ClothesExtractionControllerTest {

    private static final String URL = "/api/clothes/extractions";
    private static final String PAGE_URL = "https://shop.example/p/1";

    @Mock
    private ClothesExtractionService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClothesExtractionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
                .build();
    }

    @Test
    void 링크를_서비스에_그대로_넘기고_200과_의상_정보를_반환한다() throws Exception {
        UUID definitionId = UUID.randomUUID();
        given(service.extract(PAGE_URL)).willReturn(new ClothesDto(null, null, "오버핏 블랙 자켓",
                "https://cdn.shop.example/a.jpg", ClothesType.OUTER,
                List.of(new ClothesAttributeWithDefDto(definitionId, "색상", List.of("블랙"), "블랙"))));

        mockMvc.perform(get(URL).param("url", PAGE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("오버핏 블랙 자켓"))
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.shop.example/a.jpg"))
                .andExpect(jsonPath("$.type").value("OUTER"))
                .andExpect(jsonPath("$.attributes[0].definitionId").value(definitionId.toString()))
                .andExpect(jsonPath("$.attributes[0].value").value("블랙"));

        verify(service).extract(PAGE_URL);
    }

    @Test
    void url이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));

        verifyNoInteractions(service);
    }

    @Test
    void 허용되지_않는_주소면_400과_메시지를_반환한다() throws Exception {
        given(service.extract(anyString())).willThrow(new OtbooException(ErrorCode.CLOTHES_EXTRACTION_INVALID_URL));

        mockMvc.perform(get(URL).param("url", "http://127.0.0.1/"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.CLOTHES_EXTRACTION_INVALID_URL.getMessage()));
    }

    @Test
    void 페이지를_가져오지_못하면_502를_반환한다() throws Exception {
        given(service.extract(anyString())).willThrow(new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED));

        mockMvc.perform(get(URL).param("url", PAGE_URL))
                .andExpect(status().is(ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED.getStatus().value()))
                .andExpect(jsonPath("$.message").value(ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED.getMessage()));
    }

    @Test
    void 의상_정보를_찾지_못하면_422를_반환한다() throws Exception {
        given(service.extract(anyString())).willThrow(new OtbooException(ErrorCode.CLOTHES_EXTRACTION_NOT_FOUND));

        mockMvc.perform(get(URL).param("url", PAGE_URL))
                .andExpect(status().is(ErrorCode.CLOTHES_EXTRACTION_NOT_FOUND.getStatus().value()))
                .andExpect(jsonPath("$.message").value(ErrorCode.CLOTHES_EXTRACTION_NOT_FOUND.getMessage()));
    }
}
