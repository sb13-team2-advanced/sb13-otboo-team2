package codeit.ootd.domain.clothes.controller;

import static org.hamcrest.Matchers.hasItem;
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
import codeit.ootd.domain.clothes.exception.ClothesAttributeDefinitionNameDuplicatedException;
import codeit.ootd.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import codeit.ootd.domain.clothes.exception.InvalidSortFieldException;
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
    private static final String VALIDATION_MESSAGE = "요청 값이 올바르지 않습니다.";

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
    void 이름이_비어_있으면_400과_공통_오류_응답을_반환한다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"selectableValues\":[\"a\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                .andExpect(jsonPath("$.details.name").value("속성 정의 이름은 필수입니다."));

        verifyNoInteractions(service);
    }

    @Test
    void 선택_가능한_값이_없으면_400과_실패_사유를_반환한다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"두께\",\"selectableValues\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                .andExpect(jsonPath("$.details.selectableValues").value("선택 가능한 값은 1개 이상이어야 합니다."));

        verifyNoInteractions(service);
    }

    @Test
    void 선택_가능한_값에_빈_문자열이_있으면_400과_실패_사유를_반환한다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"두께\",\"selectableValues\":[\"a\",\" \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                .andExpect(jsonPath("$.details.*", hasItem("선택 가능한 값은 비어 있을 수 없습니다.")));

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
                .andExpect(jsonPath("$.message").value("이미 존재하는 속성 정의 이름입니다."));
    }

    @Test
    void 목록은_배열로_반환하고_정렬과_검색_파라미터를_서비스로_전달한다() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.list(eq("name"), eq(SortDirection.ASCENDING), eq("두")))
                .willReturn(List.of(new ClothesAttributeDefDto(
                        id, Instant.parse("2026-10-27T01:00:00Z"), "두께", List.of("얇음"))));

        mockMvc.perform(get(URL)
                        .param("sortBy", "name")
                        .param("sortDirection", "ASCENDING")
                        .param("keywordLike", "두"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("두께"))
                .andExpect(jsonPath("$[0].selectableValues[0]").value("얇음"))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void 목록_조회는_limit_없이도_호출할_수_있고_보내도_무시한다() throws Exception {
        given(service.list(isNull(), isNull(), isNull())).willReturn(List.of());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get(URL).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void 지원하지_않는_정렬_기준이면_400을_반환한다() throws Exception {
        given(service.list(eq("likeCount"), isNull(), isNull())).willThrow(new InvalidSortFieldException());

        mockMvc.perform(get(URL).param("sortBy", "likeCount"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("InvalidSortFieldException"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 정렬 기준입니다."));
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
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exceptionName").value("ClothesAttributeDefinitionNotFoundException"));
    }

    @Test
    void 수정_요청의_이름이_공백이면_400과_실패_사유를_반환한다() throws Exception {
        mockMvc.perform(patch(URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                .andExpect(jsonPath("$.details.name").value("속성 정의 이름은 비어 있을 수 없습니다."));

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
