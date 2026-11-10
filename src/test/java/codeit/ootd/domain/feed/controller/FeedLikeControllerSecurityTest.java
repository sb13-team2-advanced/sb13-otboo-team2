package codeit.ootd.domain.feed.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.feed.service.FeedLikeService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-like-security;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@DisplayName("피드 좋아요 API 인증 연동 테스트")
class FeedLikeControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FeedLikeService feedLikeService;

    @Test
    @DisplayName("유효한 JWT의 사용자 ID를 Service에 전달하고 204를 반환한다")
    void likesFeedWithValidJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        String token = validAccessToken(userId);

        mockMvc.perform(postWithCsrf(feedId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(feedLikeService).like(feedId, userId);
    }

    @Test
    @DisplayName("JWT 없이 좋아요 요청을 보내면 401로 거절한다")
    void rejectsMissingJwt() throws Exception {
        mockMvc.perform(postWithCsrf(UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedLikeService);
    }

    @Test
    @DisplayName("잘못된 JWT로 좋아요 요청을 보내면 401로 거절한다")
    void rejectsInvalidJwt() throws Exception {
        mockMvc.perform(postWithCsrf(UUID.randomUUID())
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedLikeService);
    }

    @Test
    @DisplayName("유효한 JWT가 있어도 CSRF가 없으면 403으로 거절한다")
    void rejectsMissingCsrf() throws Exception {
        String token = validAccessToken(UUID.randomUUID());

        mockMvc.perform(post("/api/feeds/{feedId}/like", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.ACCESS_DENIED.getMessage()
                ));

        verifyNoInteractions(feedLikeService);
    }

    @Test
    @DisplayName("유효한 JWT가 있어도 CSRF 없이 좋아요를 취소하면 403으로 거절한다")
    void rejectsUnlikeWithoutCsrf() throws Exception {
        String token = validAccessToken(UUID.randomUUID());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/feeds/{feedId}/like", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.ACCESS_DENIED.getMessage()
                ));

        verifyNoInteractions(feedLikeService);
    }

    @Test
    @DisplayName("유효한 JWT와 CSRF로 좋아요를 취소하면 로그인 사용자 ID를 전달하고 204를 반환한다")
    void unlikesFeedWithValidJwtAndCsrf() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        String token = validAccessToken(userId);

        MockHttpServletRequestBuilder request =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/feeds/{feedId}/like", feedId);

        mockMvc.perform(withCsrf(request)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(feedLikeService).unlike(feedId, userId);
    }

    @Test
    @DisplayName("JWT 없이 좋아요 취소를 요청하면 401로 거절한다")
    void rejectsUnlikeWithoutJwt() throws Exception {
        MockHttpServletRequestBuilder request =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/feeds/{feedId}/like", UUID.randomUUID());

        mockMvc.perform(withCsrf(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedLikeService);
    }

    private MockHttpServletRequestBuilder postWithCsrf(UUID feedId) throws Exception {
        return withCsrf(post("/api/feeds/{feedId}/like", feedId));
    }

    private MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");

        assertNotNull(csrfCookie);

        return request.cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue());
    }

    private String validAccessToken(UUID userId) {
        UUID sessionId = UUID.randomUUID();

        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(userId);
        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRole()).thenReturn(UserRole.USER);
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        return jwtProvider.generateAccessToken(userId, sessionId);
    }
}