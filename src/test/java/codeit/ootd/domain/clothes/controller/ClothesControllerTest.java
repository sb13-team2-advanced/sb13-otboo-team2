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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDtoCursorResponse;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.exception.ClothesInUseException;
import codeit.ootd.domain.clothes.exception.ClothesInvalidCursorException;
import codeit.ootd.domain.clothes.exception.ClothesNotFoundException;
import codeit.ootd.domain.clothes.exception.ClothesOwnerNotFoundException;
import codeit.ootd.domain.clothes.service.ClothesService;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ClothesControllerTest {

    private static final String URL = "/api/clothes";
    private static final String INVALID_REQUEST = "요청 값이 올바르지 않습니다.";

    @Mock
    private ClothesService service;

    private MockMvc mockMvc;
    private JsonMapper objectMapper;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        objectMapper = JsonMapper.builder().build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClothesController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    // ---------- 등록 ----------

    @Test
    void 등록하면_201과_의상을_반환하고_요청_값을_서비스에_넘긴다() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        ClothesDto dto = dto(ownerId, definitionId);
        given(service.create(any(), any())).willReturn(dto);
        ClothesCreateRequest body = new ClothesCreateRequest(ownerId, "셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeRequest(definitionId, "검정")));
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart(URL).file(jsonPart(body)).file(image))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(dto.id().toString()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.name").value("셔츠"))
                .andExpect(jsonPath("$.imageUrl").value("/files/clothes/a.png"))
                .andExpect(jsonPath("$.type").value("TOP"))
                .andExpect(jsonPath("$.attributes[0].definitionId").value(definitionId.toString()))
                .andExpect(jsonPath("$.attributes[0].definitionName").value("색상"))
                .andExpect(jsonPath("$.attributes[0].selectableValues[1]").value("흰색"))
                .andExpect(jsonPath("$.attributes[0].value").value("검정"));

        ArgumentCaptor<ClothesCreateRequest> requestCaptor = ArgumentCaptor.forClass(ClothesCreateRequest.class);
        ArgumentCaptor<MultipartFile> imageCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(service).create(requestCaptor.capture(), imageCaptor.capture());
        assertThat(requestCaptor.getValue()).isEqualTo(body);
        assertThat(imageCaptor.getValue().getOriginalFilename()).isEqualTo("a.png");
    }

    @Test
    void 이미지_없이도_등록할_수_있다() throws Exception {
        UUID ownerId = UUID.randomUUID();
        given(service.create(any(), any())).willReturn(dto(ownerId, UUID.randomUUID()));

        mockMvc.perform(multipart(URL).file(jsonPart(
                        new ClothesCreateRequest(ownerId, "셔츠", ClothesType.TOP, null))))
                .andExpect(status().isCreated());

        verify(service).create(any(), isNull());
    }

    @Test
    void 이름이_비어_있으면_400과_필드별_사유를_반환한다() throws Exception {
        ClothesCreateRequest body = new ClothesCreateRequest(UUID.randomUUID(), " ", ClothesType.TOP, null);

        mockMvc.perform(multipart(URL).file(jsonPart(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST))
                .andExpect(jsonPath("$.details.name").value("의상 이름은 필수입니다."));

        verifyNoInteractions(service);
    }

    @Test
    void 타입과_소유자가_없으면_필드별_사유가_모두_담긴다() throws Exception {
        mockMvc.perform(multipart(URL).file(jsonPart(Map.of("name", "셔츠"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.ownerId").value("소유자 ID는 필수입니다."))
                .andExpect(jsonPath("$.details.type").value("의상 타입은 필수입니다."));

        verifyNoInteractions(service);
    }

    @Test
    void 속성_값이_비어_있으면_속성_위치가_담긴_사유를_반환한다() throws Exception {
        ClothesCreateRequest body = new ClothesCreateRequest(UUID.randomUUID(), "셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeRequest(UUID.randomUUID(), "")));

        mockMvc.perform(multipart(URL).file(jsonPart(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST))
                .andExpect(jsonPath("$.details['attributes[0].value']").value("속성 값은 필수입니다."));

        verifyNoInteractions(service);
    }

    @Test
    void 알_수_없는_타입이면_400과_공통_메시지를_반환한다() throws Exception {
        String json = "{\"ownerId\":\"" + UUID.randomUUID() + "\",\"name\":\"셔츠\",\"type\":\"NOPE\"}";
        MockMultipartFile part = new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart(URL).file(part))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST));

        verifyNoInteractions(service);
    }

    @Test
    void request_파트가_없으면_400이다() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart(URL).file(image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST));

        verifyNoInteractions(service);
    }

    @Test
    void 소유자가_없으면_404와_메시지를_반환한다() throws Exception {
        given(service.create(any(), any())).willThrow(new ClothesOwnerNotFoundException());

        mockMvc.perform(multipart(URL).file(jsonPart(
                        new ClothesCreateRequest(UUID.randomUUID(), "셔츠", ClothesType.TOP, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exceptionName").value("ClothesOwnerNotFoundException"))
                .andExpect(jsonPath("$.message").value("의상 소유자를 찾을 수 없습니다."));
    }

    @Test
    void 파일이_너무_크면_413과_메시지를_반환한다() throws Exception {
        given(service.create(any(), any())).willThrow(new MaxUploadSizeExceededException(10));

        mockMvc.perform(multipart(URL).file(jsonPart(
                        new ClothesCreateRequest(UUID.randomUUID(), "셔츠", ClothesType.TOP, null))))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.message").value("파일 크기가 너무 큽니다."));
    }

    // ---------- 목록 ----------

    @Test
    void 목록을_조회하면_커서_응답을_반환한다() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID nextIdAfter = UUID.randomUUID();
        given(service.list(ownerId, null, null, null, 20)).willReturn(new ClothesDtoCursorResponse(
                List.of(dto(ownerId, UUID.randomUUID())), "2026-10-28T09:00:00Z", nextIdAfter, true, 7L,
                "createdAt", SortDirection.DESCENDING));

        mockMvc.perform(get(URL).param("ownerId", ownerId.toString()).param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("셔츠"))
                .andExpect(jsonPath("$.nextCursor").value("2026-10-28T09:00:00Z"))
                .andExpect(jsonPath("$.nextIdAfter").value(nextIdAfter.toString()))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalCount").value(7))
                .andExpect(jsonPath("$.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
    }

    @Test
    void 타입과_커서_조건을_서비스에_넘긴다() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID idAfter = UUID.randomUUID();
        given(service.list(any(), any(), any(), any(), eq(5))).willReturn(new ClothesDtoCursorResponse(
                List.of(), null, null, false, 0L, "createdAt", SortDirection.DESCENDING));

        mockMvc.perform(get(URL)
                        .param("ownerId", ownerId.toString())
                        .param("typeEqual", "SHOES")
                        .param("cursor", "2026-10-28T09:00:00Z")
                        .param("idAfter", idAfter.toString())
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(service).list(ownerId, ClothesType.SHOES, "2026-10-28T09:00:00Z", idAfter, 5);
    }

    @Test
    void 필수_파라미터가_없거나_형식이_틀리면_400과_공통_메시지를_반환한다() throws Exception {
        String ownerId = UUID.randomUUID().toString();

        mockMvc.perform(get(URL).param("ownerId", ownerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST));
        mockMvc.perform(get(URL).param("limit", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST));
        mockMvc.perform(get(URL).param("ownerId", "not-a-uuid").param("limit", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST));
        mockMvc.perform(get(URL).param("ownerId", ownerId).param("limit", "20").param("typeEqual", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST));

        verifyNoInteractions(service);
    }

    @Test
    void 커서나_조회_개수가_잘못되면_400과_메시지를_반환한다() throws Exception {
        given(service.list(any(), any(), any(), any(), eq(1000))).willThrow(new ClothesInvalidCursorException());

        mockMvc.perform(get(URL).param("ownerId", UUID.randomUUID().toString()).param("limit", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("ClothesInvalidCursorException"))
                .andExpect(jsonPath("$.message").value("커서 또는 조회 개수가 올바르지 않습니다."));
    }

    // ---------- 수정 ----------

    @Test
    void 일부_필드만_보내도_수정되고_보내지_않은_값은_null로_전달된다() throws Exception {
        UUID clothesId = UUID.randomUUID();
        given(service.update(eq(clothesId), any(), any())).willReturn(dto(UUID.randomUUID(), UUID.randomUUID()));

        mockMvc.perform(multipart(HttpMethod.PATCH, URL + "/{id}", clothesId)
                        .file(jsonPart(Map.of("name", "새 이름"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("셔츠"));

        ArgumentCaptor<ClothesUpdateRequest> captor = ArgumentCaptor.forClass(ClothesUpdateRequest.class);
        verify(service).update(eq(clothesId), captor.capture(), isNull());
        assertThat(captor.getValue()).isEqualTo(new ClothesUpdateRequest("새 이름", null, null));
    }

    @Test
    void 수정_이름이_공백이면_400과_필드별_사유를_반환한다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, URL + "/{id}", UUID.randomUUID())
                        .file(jsonPart(Map.of("name", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST))
                .andExpect(jsonPath("$.details.name").value("의상 이름은 비어 있을 수 없습니다."));

        verifyNoInteractions(service);
    }

    @Test
    void 수정할_의상이_없으면_404와_메시지를_반환한다() throws Exception {
        given(service.update(any(), any(), any())).willThrow(new ClothesNotFoundException());

        mockMvc.perform(multipart(HttpMethod.PATCH, URL + "/{id}", UUID.randomUUID())
                        .file(jsonPart(Map.of("name", "새 이름"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exceptionName").value("ClothesNotFoundException"))
                .andExpect(jsonPath("$.message").value("의상을 찾을 수 없습니다."));
    }

    // ---------- 삭제 ----------

    @Test
    void 삭제하면_204를_반환한다() throws Exception {
        UUID clothesId = UUID.randomUUID();

        mockMvc.perform(delete(URL + "/{id}", clothesId))
                .andExpect(status().isNoContent());

        verify(service).delete(clothesId);
    }

    @Test
    void 삭제할_의상이_없으면_404를_반환한다() throws Exception {
        doThrow(new ClothesNotFoundException()).when(service).delete(any());

        mockMvc.perform(delete(URL + "/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("의상을 찾을 수 없습니다."));
    }

    @Test
    void 피드에서_사용_중이면_409와_메시지를_반환한다() throws Exception {
        doThrow(new ClothesInUseException()).when(service).delete(any());

        mockMvc.perform(delete(URL + "/{id}", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.exceptionName").value("ClothesInUseException"))
                .andExpect(jsonPath("$.message").value("피드에서 사용 중인 의상은 삭제할 수 없습니다."));
    }

    @Test
    void 삭제_경로의_id가_UUID가_아니면_400이다() throws Exception {
        mockMvc.perform(delete(URL + "/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST));

        verifyNoInteractions(service);
    }

    // ---------- 준비 ----------

    private MockMultipartFile jsonPart(Object body) {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(body));
    }

    private ClothesDto dto(UUID ownerId, UUID definitionId) {
        return new ClothesDto(UUID.randomUUID(), ownerId, "셔츠", "/files/clothes/a.png", ClothesType.TOP,
                List.of(new ClothesAttributeWithDefDto(definitionId, "색상", List.of("검정", "흰색"), "검정")));
    }
}
