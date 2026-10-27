package codeit.ootd.domain.clothes.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefCreateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDto;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDtoCursorResponse;
import codeit.ootd.domain.clothes.exception.ClothesAttributeDefinitionNameDuplicatedException;
import codeit.ootd.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import codeit.ootd.domain.clothes.service.ClothesAttributeDefinitionService;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ClothesAttributeDefinitionControllerTest {

    private static final String URL = "/api/clothes/attribute-defs";

    @Mock
    private ClothesAttributeDefinitionService service;

    private MockMvc mockMvc;
    private JsonMapper objectMapper;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        objectMapper = JsonMapper.builder().build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClothesAttributeDefinitionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void 등록하면_201과_생성된_정의를_반환한다() throws Exception {
        UUID id = UUID.randomUUID();
        ClothesAttributeDefCreateRequest request =
                new ClothesAttributeDefCreateRequest("두께", List.of("얇음", "두꺼움"));
        given(service.create(request)).willReturn(
                new ClothesAttributeDefDto(id, Instant.parse("2026-10-27T01:00:00Z"), "두께", List.of("얇음", "두꺼움")));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("두께"))
                .andExpect(jsonPath("$.selectableValues[0]").value("얇음"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void 이름이_비어_있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"selectableValues\":[\"a\"]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 선택_가능한_값이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"두께\",\"selectableValues\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 선택_가능한_값에_빈_문자열이_있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"두께\",\"selectableValues\":[\"a\",\" \"]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 이름이_중복되면_409와_예외_정보를_반환한다() throws Exception {
        given(service.create(any())).willThrow(new ClothesAttributeDefinitionNameDuplicatedException());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"두께\",\"selectableValues\":[\"a\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.exceptionName").value("ClothesAttributeDefinitionNameDuplicatedException"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 목록_조회_파라미터를_서비스로_전달하고_커서_응답을_반환한다() throws Exception {
        UUID nextId = UUID.randomUUID();
        given(service.list(isNull(), isNull(), eq(10), eq("name"), eq(SortDirection.ASCENDING), eq("두")))
                .willReturn(new ClothesAttributeDefDtoCursorResponse(
                        List.of(), "두께", nextId, true, 3L, "name", SortDirection.ASCENDING));

        mockMvc.perform(get(URL)
                        .param("limit", "10")
                        .param("sortBy", "name")
                        .param("sortDirection", "ASCENDING")
                        .param("keywordLike", "두"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value("두께"))
                .andExpect(jsonPath("$.nextIdAfter").value(nextId.toString()))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.sortBy").value("name"))
                .andExpect(jsonPath("$.sortDirection").value("ASCENDING"));
    }

    @Test
    void 목록_조회에_limit이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 수정하면_200과_수정된_정의를_반환한다() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.update(eq(id), any())).willReturn(
                new ClothesAttributeDefDto(id, Instant.parse("2026-10-27T01:00:00Z"), "소재", List.of("면")));

        mockMvc.perform(patch(URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"소재\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("소재"));
    }

    @Test
    void 없는_정의를_수정하면_404를_반환한다() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.update(eq(id), any())).willThrow(new ClothesAttributeDefinitionNotFoundException());

        mockMvc.perform(patch(URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"소재\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 수정_요청의_이름이_공백이면_400을_반환한다() throws Exception {
        mockMvc.perform(patch(URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 삭제하면_204를_반환한다() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(URL + "/" + id))
                .andExpect(status().isNoContent());

        verify(service).delete(id);
    }

    @Test
    void 없는_정의를_삭제하면_404를_반환한다() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ClothesAttributeDefinitionNotFoundException()).when(service).delete(id);

        mockMvc.perform(delete(URL + "/" + id))
                .andExpect(status().isNotFound());
    }
}
