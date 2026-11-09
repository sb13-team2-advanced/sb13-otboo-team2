package codeit.ootd.domain.feed.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.feed.service.FeedLikeService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import codeit.ootd.global.exception.OtbooException;
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
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 좋아요 Controller 테스트")
class FeedLikeControllerTest {

    @Mock
    private FeedLikeService feedLikeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedLikeController(feedLikeService))
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
    @DisplayName("피드 ID와 인증된 사용자 ID를 전달하고 본문 없이 204를 반환한다")
    void likesFeedWithAuthenticatedUserId() throws Exception {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        mockMvc.perform(post("/api/feeds/{feedId}/like", feedId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(feedLikeService).like(feedId, userId);
    }

    @ParameterizedTest(name = "{0}에 맞는 오류 응답을 반환한다")
    @EnumSource(
            value = ErrorCode.class,
            names = {
                    "INVALID_REQUEST",
                    "FEED_NOT_FOUND"
            }
    )
    @DisplayName("서비스 예외를 해당 상태 코드와 오류 메시지로 반환한다")
    void returnsServiceErrorResponse(ErrorCode errorCode) throws Exception {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        doThrow(new OtbooException(errorCode))
                .when(feedLikeService)
                .like(feedId, userId);

        mockMvc.perform(post("/api/feeds/{feedId}/like", feedId))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.exceptionName")
                        .value("OtbooException"))
                .andExpect(jsonPath("$.message")
                        .value(errorCode.getMessage()));

        verify(feedLikeService).like(feedId, userId);
    }

    @Test
    @DisplayName("좋아요 취소 시 피드 ID와 인증된 사용자 ID를 전달하고 본문 없이 204를 반환한다")
    void unlikesFeedWithAuthenticatedUserId() throws Exception {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/feeds/{feedId}/like", feedId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(feedLikeService).unlike(feedId, userId);
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