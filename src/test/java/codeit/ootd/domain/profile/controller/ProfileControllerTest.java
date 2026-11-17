package codeit.ootd.domain.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.profile.dto.request.ProfileLocationRequest;
import codeit.ootd.domain.profile.dto.request.ProfileUpdateRequest;
import codeit.ootd.domain.profile.dto.response.ProfileDto;
import codeit.ootd.domain.profile.enums.Gender;
import codeit.ootd.domain.profile.exception.ProfileUserNotFoundException;
import codeit.ootd.domain.profile.service.ProfileService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import codeit.ootd.global.exception.OtbooException;
import java.time.LocalDate;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    private static final String URL = "/api/users/{userId}/profiles";
    private static final String INVALID_REQUEST = "요청 값이 올바르지 않습니다.";

    @Mock
    private ProfileService service;

    private MockMvc mockMvc;
    private JsonMapper objectMapper;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        objectMapper = JsonMapper.builder().build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    // ---------- 조회 ----------

    @Test
    void 조회하면_200과_프로필을_반환한다() throws Exception {
        UUID userId = UUID.randomUUID();
        given(service.getProfile(userId)).willReturn(dto(userId));

        mockMvc.perform(get(URL, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("완이"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.birthDate").value("1999-05-06"))
                .andExpect(jsonPath("$.temperatureSensitivity").value(3))
                .andExpect(jsonPath("$.profileImageUrl").value("/files/profiles/a.png"));
    }

    @Test
    void 조회할_사용자가_없으면_404와_메시지를_반환한다() throws Exception {
        given(service.getProfile(any())).willThrow(new ProfileUserNotFoundException());

        mockMvc.perform(get(URL, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exceptionName").value("ProfileUserNotFoundException"))
                .andExpect(jsonPath("$.message").value("프로필의 사용자를 찾을 수 없습니다."));
    }

    @Test
    void 사용자_id가_UUID가_아니면_400이다() throws Exception {
        mockMvc.perform(get(URL, "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // ---------- 수정 ----------

    @Test
    void 수정하면_200과_프로필을_반환하고_요청_값을_서비스에_넘긴다() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        given(service.updateProfile(eq(userId), eq(userId), any(), any())).willReturn(dto(userId));
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[] {1});
        Map<String, Object> body = Map.of("name", "완이", "gender", "MALE", "birthDate", "1999-05-06",
                "temperatureSensitivity", 3);

        mockMvc.perform(multipart(HttpMethod.PATCH, URL, userId).file(jsonPart(body)).file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("완이"));

        ArgumentCaptor<ProfileUpdateRequest> requestCaptor = ArgumentCaptor.forClass(ProfileUpdateRequest.class);
        ArgumentCaptor<MultipartFile> imageCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(service).updateProfile(eq(userId), eq(userId), requestCaptor.capture(), imageCaptor.capture());
        assertThat(requestCaptor.getValue())
                .isEqualTo(new ProfileUpdateRequest("완이", Gender.MALE, LocalDate.of(1999, 5, 6), null, 3));
        assertThat(imageCaptor.getValue().getOriginalFilename()).isEqualTo("a.png");
    }

    @Test
    void 일부_값만_보내고_이미지가_없어도_수정된다() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        given(service.updateProfile(eq(userId), eq(userId), any(), any())).willReturn(dto(userId));

        mockMvc.perform(multipart(HttpMethod.PATCH, URL, userId).file(jsonPart(Map.of("temperatureSensitivity", 5))))
                .andExpect(status().isOk());

        verify(service).updateProfile(eq(userId), eq(userId),
                eq(new ProfileUpdateRequest(null, null, null, null, 5)), isNull());
    }

    @Test
    void 이름이_공백이면_400과_필드별_사유를_반환한다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID()).file(jsonPart(Map.of("name", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_REQUEST))
                .andExpect(jsonPath("$.details.name").exists());

        verifyNoInteractions(service);
    }

    @Test
    void 미래_생년월일이면_400과_필드별_사유를_반환한다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID())
                        .file(jsonPart(Map.of("birthDate", "2999-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.birthDate").exists());

        verifyNoInteractions(service);
    }

    @Test
    void 온도_민감도가_범위를_벗어나면_400과_필드별_사유를_반환한다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID())
                        .file(jsonPart(Map.of("temperatureSensitivity", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.temperatureSensitivity").exists());
        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID())
                        .file(jsonPart(Map.of("temperatureSensitivity", 6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.temperatureSensitivity").exists());

        verifyNoInteractions(service);
    }

    @Test
    void 위치를_보내면_위경도만_서비스에_넘기고_x_y_지명은_무시한다() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        given(service.updateProfile(eq(userId), eq(userId), any(), any())).willReturn(dto(userId));
        Map<String, Object> location = Map.of("latitude", 37.5, "longitude", 127.0, "x", 60, "y", 127,
                "locationNames", java.util.List.of("서울특별시"));

        mockMvc.perform(multipart(HttpMethod.PATCH, URL, userId).file(jsonPart(Map.of("location", location))))
                .andExpect(status().isOk());

        verify(service).updateProfile(eq(userId), eq(userId),
                eq(new ProfileUpdateRequest(null, null, null, new ProfileLocationRequest(37.5, 127.0), null)),
                isNull());
    }

    @Test
    void 위도_경도가_범위를_벗어나거나_빠지면_400과_필드별_사유를_반환한다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID())
                        .file(jsonPart(Map.of("location", Map.of("latitude", 91.0, "longitude", 127.0)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details['location.latitude']").exists());
        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID())
                        .file(jsonPart(Map.of("location", Map.of("latitude", 37.5)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details['location.longitude']").exists());

        verifyNoInteractions(service);
    }

    @Test
    void 알_수_없는_성별이면_400이다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID()).file(jsonPart(Map.of("gender", "NOPE"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void request_파트가_없으면_400이다() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID()).file(image))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 수정할_사용자가_없으면_404와_메시지를_반환한다() throws Exception {
        given(service.updateProfile(any(), any(), any(), any())).willThrow(new ProfileUserNotFoundException());

        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID()).file(jsonPart(Map.of("name", "완이"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("프로필의 사용자를 찾을 수 없습니다."));
    }

    @Test
    void 다른_사용자의_프로필을_수정하면_403을_반환한다() throws Exception {
        authenticate(UUID.randomUUID());
        given(service.updateProfile(any(), any(), any(), any()))
                .willThrow(new OtbooException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID()).file(jsonPart(Map.of("name", "완이"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorCode.ACCESS_DENIED.getMessage()));
    }

    @Test
    void 이미지_저장_중_업무_예외가_나면_해당_상태와_메시지를_반환한다() throws Exception {
        given(service.updateProfile(any(), any(), any(), any())).willThrow(new OtbooException(ErrorCode.FILE_INVALID_TYPE));
        MockMultipartFile image = new MockMultipartFile("image", "a.txt", "text/plain", new byte[] {1});

        mockMvc.perform(multipart(HttpMethod.PATCH, URL, UUID.randomUUID())
                        .file(jsonPart(Map.of("name", "완이"))).file(image))
                .andExpect(status().is(ErrorCode.FILE_INVALID_TYPE.getStatus().value()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FILE_INVALID_TYPE.getMessage()));
    }

    // ---------- 준비 ----------

    private void authenticate(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of()));
    }

    private MockMultipartFile jsonPart(Object body) {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(body));
    }

    private ProfileDto dto(UUID userId) {
        return new ProfileDto(userId, "완이", Gender.MALE, LocalDate.of(1999, 5, 6), null, 3,
                "/files/profiles/a.png");
    }
}
