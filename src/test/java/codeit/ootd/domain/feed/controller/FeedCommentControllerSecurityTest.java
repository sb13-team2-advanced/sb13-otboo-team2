package codeit.ootd.domain.feed.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.service.FeedCommentService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-comment-security;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@DisplayName("피드 댓글 등록 API 인증 연동 테스트")
class FeedCommentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FeedCommentService feedCommentService;

    private final UUID feedId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String content = "코트 색이 잘 어울려요.";

    @Test
    @DisplayName("유효한 JWT와 CSRF 토큰으로 댓글을 등록하면 200과 등록된 댓글을 반환한다")
    void createsCommentWithValidJwtAndCsrf() throws Exception {
        UUID commentId = UUID.randomUUID();
        CommentCreateRequest request = new CommentCreateRequest(feedId, userId, content);
        CommentDto response = new CommentDto(
                commentId, Instant.parse("2026-11-09T06:00:00Z"), feedId,
                new AuthorDto(userId, "리아", null), content
        );
        when(feedCommentService.create(feedId, request, userId)).thenReturn(response);

        mockMvc.perform(withCsrf(commentRequest())
                        .header("Authorization", "Bearer " + validAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.author.userId").value(userId.toString()));

        verify(feedCommentService).create(feedId, request, userId);
    }

    @Test
    @DisplayName("CSRF 토큰이 있어도 JWT가 없으면 401을 반환하고 댓글을 등록하지 않는다")
    void rejectsMissingJwt() throws Exception {
        mockMvc.perform(withCsrf(commentRequest()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(feedCommentService);
    }

    @Test
    @DisplayName("유효한 JWT가 있어도 CSRF 토큰이 없으면 403을 반환하고 댓글을 등록하지 않는다")
    void rejectsMissingCsrf() throws Exception {
        mockMvc.perform(commentRequest()
                        .header("Authorization", "Bearer " + validAccessToken()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(feedCommentService);
    }

    private MockHttpServletRequestBuilder commentRequest() {
        return post("/api/feeds/{feedId}/comments", feedId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"feedId":"%s","authorId":"%s","content":"%s"}
                        """.formatted(feedId, userId, content));
    }

    private MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        Cookie cookie = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");

        assertNotNull(cookie);
        return request.cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue());
    }

    private String validAccessToken() {
        UUID sessionId = UUID.randomUUID();
        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(userId);
        when(authSessionService.getActiveSession(sessionId)).thenReturn(session);

        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRole()).thenReturn(UserRole.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        return jwtProvider.generateAccessToken(userId, sessionId);
    }
}