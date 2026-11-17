package codeit.ootd.domain.user.integration;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
class UserListSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("USER 권한으로 사용자 목록을 조회하면 200을 반환한다")
    void getUsers_withUserRole_returnsOk() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String accessToken = jwtProvider.generateAccessToken(userId, sessionId);

        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(userId);
        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRole()).thenReturn(UserRole.USER);
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(userRepository.findUsers(
                null, null, 10, "email", "ASCENDING",
                null, null, null
        )).thenReturn(new org.springframework.data.domain.SliceImpl<>(
                List.of(),
                org.springframework.data.domain.PageRequest.of(0, 10),
                false
        ));

        when(userRepository.countUsers(null, null, null))
                .thenReturn(0L);

        // when & then
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(0));

        verify(authSessionService).getActiveSession(sessionId);
        verify(userRepository).findById(userId);
        verify(userRepository).findUsers(
                null, null, 10, "email", "ASCENDING",
                null, null, null
        );
    }

    @Test
    @DisplayName("인증 없이 사용자 목록을 조회하면 401을 반환한다")
    void getUsers_withoutToken_returnsUnauthorized() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users")
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exceptionName")
                        .value("OtbooException"))
                .andExpect(jsonPath("$.message")
                        .value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap());

        verifyNoInteractions(authSessionService);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("ADMIN 권한으로 사용자 목록을 조회하면 200을 반환한다")
    void getUsers_withAdminRole_returnsOk() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String accessToken = jwtProvider.generateAccessToken(userId, sessionId);

        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(userId);
        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRole()).thenReturn(UserRole.ADMIN);
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(userRepository.findUsers(
                null, null, 10, "email", "ASCENDING",
                null, null, null
        )).thenReturn(new org.springframework.data.domain.SliceImpl<>(
                List.of(),
                org.springframework.data.domain.PageRequest.of(0, 10),
                false
        ));

        // when & then
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(0));

        verify(authSessionService).getActiveSession(sessionId);
        verify(userRepository).findById(userId);
        verify(userRepository).findUsers(
                null, null, 10, "email", "ASCENDING",
                null, null, null
        );
    }


}
