package codeit.ootd.domain.feed.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.feed.dto.request.FeedUpdateRequest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
@DisplayName("피드 수정 Controller 테스트")
class FeedControllerUpdateTest {

    @Mock
    private FeedService feedService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedController(feedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(
                                JsonMapper.builder().build()
                        )
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("피드 ID와 수정 내용 및 인증된 사용자 ID를 전달하고 응답을 반환한다")
    void updatesFeedWithAuthenticatedUserId() throws Exception {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        FeedUpdateRequest request =
                new FeedUpdateRequest("수정한 내용");

        FeedDto response = new FeedDto(
                feedId,
                Instant.parse("2026-11-05T00:00:00Z"),
                Instant.parse("2026-11-05T05:00:00Z"),
                null,
                null,
                List.of(),
                "수정한 내용",
                0L,
                0,
                false
        );

        when(feedService.update(feedId, request, userId))
                .thenReturn(response);

        mockMvc.perform(patch("/api/feeds/{feedId}", feedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "수정한 내용"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(feedId.toString()))
                .andExpect(jsonPath("$.content").value("수정한 내용"));

        verify(feedService).update(feedId, request, userId);
    }

    @ParameterizedTest(name = "{0}에 맞는 오류 응답을 반환한다")
    @EnumSource(
            value = ErrorCode.class,
            names = {
                    "INVALID_REQUEST",
                    "ACCESS_DENIED",
                    "FEED_NOT_FOUND"
            }
    )
    @DisplayName("서비스 예외를 해당 상태 코드와 오류 메시지로 반환한다")
    void returnsServiceErrorResponse(ErrorCode errorCode) throws Exception {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        FeedUpdateRequest request =
                new FeedUpdateRequest("수정한 내용");

        when(feedService.update(feedId, request, userId))
                .thenThrow(new OtbooException(errorCode));

        mockMvc.perform(patch("/api/feeds/{feedId}", feedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "수정한 내용"
                                }
                                """))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.exceptionName")
                        .value("OtbooException"))
                .andExpect(jsonPath("$.message")
                        .value(errorCode.getMessage()));

        verify(feedService).update(feedId, request, userId);
    }

    private void authenticate(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                )
        );
    }
}