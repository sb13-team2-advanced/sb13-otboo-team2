package codeit.ootd.domain.feed.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.feed.service.FeedService;
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
        "spring.datasource.url=jdbc:h2:mem:feed-delete-security;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@DisplayName("피드 삭제 API 인증 연동 테스트")
class FeedControllerDeleteSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FeedService feedService;

    @Test
    @DisplayName("유효한 JWT의 사용자 ID를 Service에 전달하고 204를 반환한다")
    void deletesFeedWithValidJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        String token = validAccessToken(userId);

        mockMvc.perform(deleteWithCsrf(feedId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(feedService).delete(feedId, userId);
    }

    @Test
    @DisplayName("JWT 없이 삭제 요청을 보내면 401로 거절한다")
    void rejectsMissingJwt() throws Exception {
        mockMvc.perform(deleteWithCsrf(UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("잘못된 JWT로 삭제 요청을 보내면 401로 거절한다")
    void rejectsInvalidJwt() throws Exception {
        mockMvc.perform(deleteWithCsrf(UUID.randomUUID())
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("유효한 JWT가 있어도 CSRF가 없으면 403으로 거절한다")
    void rejectsMissingCsrf() throws Exception {
        String token = validAccessToken(UUID.randomUUID());

        mockMvc.perform(delete("/api/feeds/{feedId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.ACCESS_DENIED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    private MockHttpServletRequestBuilder deleteWithCsrf(
            UUID feedId
    ) throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");

        assertNotNull(csrfCookie);

        return delete("/api/feeds/{feedId}", feedId)
                .cookie(csrfCookie)
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