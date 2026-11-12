package codeit.ootd.domain.feed.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.service.FeedCommentService;
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
@DisplayName("피드 댓글 등록 Controller 테스트")
class FeedCommentControllerTest {

    @Mock
    private FeedCommentService feedCommentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedCommentController(feedCommentService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(JsonMapper.builder().build())
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("피드 ID와 댓글 요청, 로그인 사용자 ID를 전달하고 등록된 댓글을 반환한다")
    void createsCommentWithAuthenticatedUserId() throws Exception {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        String content = "코트 색이 잘 어울려요.";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );

        CommentCreateRequest request = new CommentCreateRequest(feedId, userId, content);
        CommentDto response = new CommentDto(
                commentId, Instant.parse("2026-11-09T06:00:00Z"), feedId,
                new AuthorDto(userId, "리아", null), content
        );
        when(feedCommentService.create(feedId, request, userId)).thenReturn(response);

        mockMvc.perform(post("/api/feeds/{feedId}/comments", feedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feedId":"%s","authorId":"%s","content":"%s"}
                                """.formatted(feedId, userId, content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.feedId").value(feedId.toString()))
                .andExpect(jsonPath("$.author.userId").value(userId.toString()))
                .andExpect(jsonPath("$.content").value(content));

        verify(feedCommentService).create(feedId, request, userId);
    }
}