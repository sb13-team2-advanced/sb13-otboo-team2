package codeit.ootd.global.security;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.InvalidAuthSessionException;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("인증 없이 보호된 API에 접근하면 401과 오류 응답을 반환한다")
    void protectedApi_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/profiles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exceptionName").value("OtbooException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    @DisplayName("테스트 전용 보호 API에 토큰 없이 접근하면 401을 반환한다")
    void testApi_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/test/authenticated"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 JWT로 보호된 API에 접근하면 200을 반환한다")
    void protectedApi_withValidToken_returnsOk() throws Exception {
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

        // when & then
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string("인증 성공"));

        verify(authSessionService).getActiveSession(sessionId);
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("폐기된 세션의 JWT로 보호된 API에 접근하면 401을 반환한다")
    void protectedApi_withRevokedSession_returnsUnauthorized() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String accessToken = jwtProvider.generateAccessToken(userId, sessionId);

        when(authSessionService.getActiveSession(sessionId))
                .thenThrow(new InvalidAuthSessionException());

        // when & then
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("유효하지 않거나 만료된 인증 세션입니다."))
                .andExpect(jsonPath("$.details").isMap());

        verify(authSessionService).getActiveSession(sessionId);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("USER 권한으로 관리자 전용 API에 접근하면 403을 반환한다")
    void adminApi_withUserRole_returnsForbidden() throws Exception {
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

        // when & then
        mockMvc.perform(get("/api/test/admin")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.details").isMap());

        verify(authSessionService).getActiveSession(sessionId);
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("ADMIN 권한으로 관리자 전용 API에 접근하면 200을 반환한다")
    void adminApi_withAdminRole_returnsOk() throws Exception {
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

        // when & then
        mockMvc.perform(get("/api/test/admin")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string("관리자 접근 성공"));

        verify(authSessionService).getActiveSession(sessionId);
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("JWT 인증 요청에서 HttpSession을 생성하지 않는다")
    void protectedApi_withValidToken_doesNotCreateSession() throws Exception {
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

        // when & then
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertNull(result.getRequest().getSession(false))
                );
    }


    /**
     * JWT 인증·인가 통합 테스트를 위한 전용 Controller 설정.
     *
     * 실제 사용자 관리 API가 아직 구현되지 않아 테스트용 엔드포인트를 사용한다.
     * 테스트 환경에서만 등록되며 운영 환경에는 포함되지 않는다.
     *
     * 실제 API 구현 후에는 해당 Controller를 대상으로
     * 인증·인가 통합 테스트를 추가할 예정이다.
     */
    @TestConfiguration
    static class TestControllerConfig {

        @Bean
        TestAuthController testAuthController() {
            return new TestAuthController();
        }
    }

    @RestController
    static class TestAuthController {

        @GetMapping("/api/test/authenticated")
        public ResponseEntity<String> authenticated() {
            return ResponseEntity.ok("인증 성공");
        }

        @GetMapping("/api/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<String> adminOnly() {
            return ResponseEntity.ok("관리자 접근 성공");
        }

    }


}

