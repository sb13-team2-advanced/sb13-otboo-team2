package codeit.ootd.domain.user.integration;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
class UserLockSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("USER 권한으로 계정 잠금 요청 시 403을 반환한다")
    void updateLock_withUserRole_returnsForbidden() throws Exception {
        // given
        UUID loginUserId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        String accessToken =
                jwtProvider.generateAccessToken(loginUserId, sessionId);

        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(loginUserId);
        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        User loginUser = mock(User.class);
        when(loginUser.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(loginUser.getRole()).thenReturn(UserRole.USER);
        when(userRepository.findById(loginUserId))
                .thenReturn(Optional.of(loginUser));

        // when & then
        mockMvc.perform(patch("/api/users/{userId}/lock", targetUserId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locked": true
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("접근 권한이 없습니다."));

        verifyNoInteractions(userService);
    }
}
