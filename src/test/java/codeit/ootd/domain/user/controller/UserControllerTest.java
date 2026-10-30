package codeit.ootd.domain.user.controller;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.dto.response.UserListResponse;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.service.UserService;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    
    @Mock
    private UserService userService;
    
    private MockMvc mockMvc;
    private JsonMapper objectMapper;

    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        objectMapper = JsonMapper.builder().build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(objectMapper)
                )
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    @DisplayName("회원가입 성공 시 201과 사용자 정보를 반환한다")
    void signup_success() throws Exception {
        // given
        UserCreateRequest request = validRequest();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-27T00:00:00Z");

        UserDto response = new UserDto(
                userId,
                createdAt,
                "test@example.com",
                "테스트사용자",
                UserRole.USER,
                false
        );

        when(userService.signup(request)).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("테스트사용자"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService).signup(request);
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 400을 반환한다")
    void signup_invalidEmailFormat_returnsBadRequest() throws Exception {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "invalid-email",
                "password123!"
        );

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("이메일이 공백이면 400을 반환한다")
    void signup_blankEmail_returnsBadRequest() throws Exception {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                " ",
                "password123!"
        );

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("이름이 공백이면 400을 반환한다")
    void signup_blankName_returnsBadRequest() throws Exception {
        // given
        UserCreateRequest request = new UserCreateRequest(
                " ",
                "test@example.com",
                "password123!"
        );

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("비밀번호가 공백이면 400을 반환한다")
    void signup_blankPassword_returnsBadRequest() throws Exception {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                " "
        );

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }


    @Test
    @DisplayName("중복된 이메일로 회원가입하면 409를 반환한다")
    void signup_duplicateEmail_returnsConflict() throws Exception {
        // given
        UserCreateRequest request = validRequest();

        doThrow(new DuplicateEmailException())
                .when(userService)
                .signup(request);

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.exceptionName")
                        .value("DuplicateEmailException"))
                .andExpect(jsonPath("$.message")
                        .value("이미 사용 중인 이메일입니다."));
    }
    
    @Test
    @DisplayName("이름이 100자를 초과하면 400 Bad Request를 반환한다")
    void signup_nameTooLong_returnsBadRequest() throws Exception {
        // given
        String requestBody = """
            {
                "name": "%s",
                "email": "test@example.com",
                "password": "password123!"
            }
            """.formatted("가".repeat(101));

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("이메일이 100자를 초과하면 400 Bad Request를 반환한다")
    void signup_emailTooLong_returnsBadRequest() throws Exception {
        // given
        String longEmail = "a".repeat(89) + "@example.com";
        // 89 + 12 = 101자

        String requestBody = """
            {
                "name": "테스트사용자",
                "email": "%s",
                "password": "password123!"
            }
            """.formatted(longEmail);

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }


    private UserCreateRequest validRequest() {
        return new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "password123!"
        );
    }

    @Test
    @DisplayName("비밀번호가 72바이트를 초과하면 400 Bad Request를 반환한다")
    void signup_passwordOver72Bytes_returnsBadRequest() throws Exception {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "a".repeat(73)
        );

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("한글 비밀번호가 72바이트를 초과하면 400 Bad Request를 반환한다")
    void signup_koreanPasswordOver72Bytes_returnsBadRequest() throws Exception {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "가".repeat(25)
        );

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("사용자 목록 조회 성공 시 200과 페이지네이션 정보를 반환한다")
    void getUsers_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-30T00:00:00Z");

        UserDto userDto = new UserDto(
                userId,
                createdAt,
                "test@example.com",
                "테스트사용자",
                UserRole.USER,
                false
        );

        UserListResponse response = new UserListResponse(
                List.of(userDto),
                null,
                null,
                false,
                1L,
                "email",
                "ASCENDING"
        );

        when(userService.getUsers(
                null, null, 10, "email", "ASCENDING",
                null, null, null
        )).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/users")
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].email").value("test@example.com"))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.sortBy").value("email"))
                .andExpect(jsonPath("$.sortDirection").value("ASCENDING"));

        verify(userService).getUsers(
                null, null, 10, "email", "ASCENDING",
                null, null, null
        );
    }

    @Test
    @DisplayName("사용자 목록 조회 시 limit가 0이면 400을 반환한다")
    void getUsers_zeroLimit_returnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users")
                        .param("limit", "0")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("사용자 목록 조회 시 limit가 음수이면 400을 반환한다")
    void getUsers_negativeLimit_returnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users")
                        .param("limit", "-1")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("InvalidUserQueryException"))
                .andExpect(jsonPath("$.message")
                        .value("잘못된 사용자 조회 요청입니다."));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("사용자 목록 조회 시 지원하지 않는 정렬 기준이면 400을 반환한다")
    void getUsers_invalidSortBy_returnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users")
                        .param("limit", "10")
                        .param("sortBy", "name")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("InvalidUserQueryException"))
                .andExpect(jsonPath("$.message")
                        .value("잘못된 사용자 조회 요청입니다."));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("사용자 목록 조회 시 지원하지 않는 정렬 방향이면 400을 반환한다")
    void getUsers_invalidSortDirection_returnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users")
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("InvalidUserQueryException"))
                .andExpect(jsonPath("$.message")
                        .value("잘못된 사용자 조회 요청입니다."));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("사용자 목록 조회 시 cursor만 전달하면 400을 반환한다")
    void getUsers_cursorWithoutIdAfter_returnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users")
                        .param("cursor", "test@example.com")
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("InvalidUserQueryException"))
                .andExpect(jsonPath("$.message")
                        .value("잘못된 사용자 조회 요청입니다."));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("생성일 커서 형식이 잘못되면 400을 반환한다")
    void getUsers_invalidCreatedAtCursor_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/users")
                        .param("cursor", "invalid-date")
                        .param("idAfter", UUID.randomUUID().toString())
                        .param("limit", "10")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("InvalidUserQueryException"))
                .andExpect(jsonPath("$.message")
                        .value("잘못된 사용자 조회 요청입니다."));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("사용자 목록 조회 시 공백 cursor를 전달하면 400을 반환한다")
    void getUsers_blankCursor_returnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users")
                        .param("cursor", " ")
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("InvalidUserQueryException"))
                .andExpect(jsonPath("$.message")
                        .value("잘못된 사용자 조회 요청입니다."));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("사용자 목록 조회 시 limit가 100을 초과하면 400을 반환한다")
    void getUsers_limitExceedsMaximum_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/users")
                        .param("limit", "101")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("InvalidUserQueryException"))
                .andExpect(jsonPath("$.message")
                        .value("잘못된 사용자 조회 요청입니다."));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("사용자 목록 조회 시 limit가 100이면 정상 처리한다")
    void getUsers_limit100_returnsOk() throws Exception {
        // given
        UserListResponse response = new UserListResponse(
                List.of(), null, null, false, 0L,
                "email", "ASCENDING"
        );

        when(userService.getUsers(
                null, null, 100, "email", "ASCENDING",
                null, null, null
        )).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/users")
                        .param("limit", "100")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(userService).getUsers(
                null, null, 100, "email", "ASCENDING",
                null, null, null
        );
    }


}
