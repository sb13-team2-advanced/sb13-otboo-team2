package codeit.ootd.domain.feed.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-controller-security;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@DisplayName("피드 등록 API 인증 연동 테스트")
class FeedControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FeedService feedService;

    @Test
    @DisplayName("유효한 JWT의 사용자 ID를 Service에 전달하고 등록한다")
    void createsFeedWithValidAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        String token = validAccessToken(userId);
        FeedCreateRequest request = request(userId);
        Instant now = Instant.parse("2026-10-30T07:00:00Z");

        FeedDto response = new FeedDto(
                feedId,
                now,
                now,
                new AuthorDto(userId, "작성자", null),
                null,
                List.of(),
                request.content(),
                0L,
                0,
                false
        );

        when(feedService.create(request, userId)).thenReturn(response);

        mockMvc.perform(postWithCsrf(request)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(feedId.toString()))
                .andExpect(jsonPath("$.author.userId")
                        .value(userId.toString()))
                .andExpect(jsonPath("$.content").value(request.content()));

        verify(feedService).create(request, userId);
    }

    @Test
    @DisplayName("JWT 없이 요청하면 401로 거절한다")
    void rejectsMissingJwt() throws Exception {
        mockMvc.perform(postWithCsrf(request(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("잘못된 JWT로 요청하면 401로 거절한다")
    void rejectsInvalidJwt() throws Exception {
        mockMvc.perform(postWithCsrf(request(UUID.randomUUID()))
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("JWT 사용자 ID를 Service에 전달하고 작성자 불일치 예외를 403으로 반환한다")
    void returnsAuthorMismatchFromService() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = validAccessToken(userId);
        FeedCreateRequest request = request(UUID.randomUUID());

        when(feedService.create(request, userId)).thenThrow(
                new OtbooException(ErrorCode.ACCESS_DENIED)
        );

        mockMvc.perform(postWithCsrf(request)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.ACCESS_DENIED.getMessage()
                ));

        verify(feedService).create(request, userId);
    }

    @Test
    @DisplayName("유효한 JWT와 사용자·세션을 준비해도 CSRF가 없으면 403으로 거절한다")
    void rejectsMissingCsrf() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = validAccessToken(userId);

        mockMvc.perform(post("/api/feeds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsBytes(request(userId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.ACCESS_DENIED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    private MockHttpServletRequestBuilder postWithCsrf(
            FeedCreateRequest request
    ) throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");

        assertNotNull(csrfCookie);

        return post("/api/feeds")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsBytes(request));
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

    private FeedCreateRequest request(UUID authorId) {
        return new FeedCreateRequest(
                authorId,
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                "오늘의 옷차림"
        );
    }
}