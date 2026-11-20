package codeit.ootd.domain.feed.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.request.FeedUpdateRequest;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 등록·수정 API 내용 검증 테스트")
class FeedControllerRequestValidationTest {

    @Mock
    private FeedService feedService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private JsonMapper jsonMapper;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );

        jsonMapper = JsonMapper.builder().build();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedController(feedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(jsonMapper)
                )
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    static Stream<String> invalidContents() {
        return Stream.of(null, "", " ", "\t\n", "가".repeat(1001));
    }

    @ParameterizedTest
    @MethodSource("invalidContents")
    @DisplayName("잘못된 등록 내용은 서비스 호출 전에 기존 등록 오류로 거절한다")
    void rejectsInvalidCreateContent(String content) throws Exception {
        FeedCreateRequest request = new FeedCreateRequest(
                userId,
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                content
        );

        mockMvc.perform(post("/api/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.FEED_CREATE_INVALID_REQUEST.getMessage()));

        verifyNoInteractions(feedService);
    }

    @ParameterizedTest
    @MethodSource("invalidContents")
    @DisplayName("잘못된 수정 내용은 서비스 호출 전에 기존 요청 오류로 거절한다")
    void rejectsInvalidUpdateContent(String content) throws Exception {
        FeedUpdateRequest request = new FeedUpdateRequest(content);

        mockMvc.perform(patch("/api/feeds/{feedId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(feedService);
    }
}