package codeit.ootd.domain.user.controller;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
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
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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


}
