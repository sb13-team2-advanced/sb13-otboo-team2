package codeit.ootd.domain.feed.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.dto.response.FeedDtoCursorResponse;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import java.util.List;
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
        "spring.datasource.url=jdbc:h2:mem:feed-list-security;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@DisplayName("피드 목록 API 인증 연동 테스트")
class FeedControllerListSecurityTest {

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
    @DisplayName("유효한 JWT의 사용자 ID를 Service에 전달하고 목록을 반환한다")
    void listsFeedsWithValidJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = validAccessToken(userId);

        FeedSearchRequest request = new FeedSearchRequest(
                null,
                null,
                20,
                "createdAt",
                SortDirection.DESCENDING,
                null,
                null,
                null,
                null
        );

        FeedDtoCursorResponse response = new FeedDtoCursorResponse(
                List.of(),
                null,
                null,
                false,
                0L,
                "createdAt",
                SortDirection.DESCENDING
        );

        when(feedService.list(request, userId)).thenReturn(response);

        mockMvc.perform(listRequest()
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(0));

        verify(feedService).list(request, userId);
    }

    @Test
    @DisplayName("JWT 없이 목록 조회를 요청하면 401로 거절한다")
    void rejectsMissingJwt() throws Exception {
        mockMvc.perform(listRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    @Test
    @DisplayName("잘못된 JWT로 목록 조회를 요청하면 401로 거절한다")
    void rejectsInvalidJwt() throws Exception {
        mockMvc.perform(listRequest()
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.AUTHENTICATION_REQUIRED.getMessage()
                ));

        verifyNoInteractions(feedService);
    }

    private MockHttpServletRequestBuilder listRequest() {
        return get("/api/feeds")
                .param("limit", "20")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING");
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