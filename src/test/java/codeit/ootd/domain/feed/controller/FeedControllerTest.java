package codeit.ootd.domain.feed.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import codeit.ootd.global.exception.OtbooException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 등록 Controller 테스트")
class FeedControllerTest {

    private static final String URL = "/api/feeds";

    @Mock
    private FeedService feedService;

    private MockMvc mockMvc;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jsonMapper = JsonMapper.builder().build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedController(feedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(jsonMapper)
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("본인 명의로 등록하면 201과 피드 응답을 반환한다")
    void createReturnsCreatedFeed() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        FeedCreateRequest request = request(userId);
        Instant createdAt = Instant.parse("2026-10-30T06:00:00Z");

        FeedDto response = new FeedDto(
                feedId,
                createdAt,
                createdAt,
                new AuthorDto(userId, "작성자", null),
                null,
                List.of(),
                request.content(),
                0L,
                0,
                false
        );

        authenticate(userId);
        when(feedService.create(request)).thenReturn(response);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(feedId.toString()))
                .andExpect(jsonPath("$.author.userId")
                        .value(userId.toString()))
                .andExpect(jsonPath("$.author.name").value("작성자"))
                .andExpect(jsonPath("$.content").value(request.content()))
                .andExpect(jsonPath("$.ootds").isArray())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(false));

        verify(feedService).create(request);
    }

    @Test
    @DisplayName("로그인 정보가 없으면 401로 거절한다")
    void rejectsMissingAuthentication() throws Exception {
        FeedCreateRequest request = request(UUID.randomUUID());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsBytes(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("로그인 사용자와 작성자가 다르면 403으로 거절한다")
    void rejectsDifferentAuthor() throws Exception {
        UUID userId = UUID.randomUUID();
        FeedCreateRequest request = request(UUID.randomUUID());
        authenticate(userId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsBytes(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.ACCESS_DENIED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("작성자 ID가 없으면 400으로 거절한다")
    void rejectsMissingAuthorId() throws Exception {
        authenticate(UUID.randomUUID());
        FeedCreateRequest request = request(null);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.FEED_CREATE_INVALID_REQUEST.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("요청 본문이 없으면 400으로 거절한다")
    void rejectsMissingBody() throws Exception {
        authenticate(UUID.randomUUID());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.INVALID_REQUEST.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("UUID 형식이 잘못되면 400으로 거절한다")
    void rejectsMalformedUuid() throws Exception {
        authenticate(UUID.randomUUID());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": "invalid-uuid",
                                  "clothesIds": [],
                                  "content": "오늘의 옷차림"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.INVALID_REQUEST.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("서비스에서 날씨를 찾지 못하면 400 오류 응답을 반환한다")
    void returnsServiceError() throws Exception {
        UUID userId = UUID.randomUUID();
        FeedCreateRequest request = request(userId);
        authenticate(userId);

        when(feedService.create(request)).thenThrow(
                new OtbooException(ErrorCode.FEED_WEATHER_NOT_FOUND)
        );

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("OtbooException"))
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.FEED_WEATHER_NOT_FOUND.getMessage()
                ));

        verify(feedService).create(request);
    }

    private FeedCreateRequest request(UUID authorId) {
        return new FeedCreateRequest(
                authorId,
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                "오늘의 옷차림"
        );
    }

    private void authenticate(UUID userId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of()
        );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }
}