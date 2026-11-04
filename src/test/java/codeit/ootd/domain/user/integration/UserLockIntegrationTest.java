package codeit.ootd.domain.user.integration;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.AccountLockedException;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
class UserLockIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired AuthSessionRepository authSessionRepository;
    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock clock;

    private UUID adminId;
    private UUID targetId;
    private UUID adminSessionId;
    private UUID targetSessionId;

    @AfterEach
    void tearDown() {
        if (adminSessionId != null) {
            authSessionRepository.deleteById(adminSessionId);
        }
        if (targetSessionId != null) {
            authSessionRepository.deleteById(targetSessionId);
        }
        if (adminId != null) {
            userRepository.deleteById(adminId);
        }
        if (targetId != null) {
            userRepository.deleteById(targetId);
        }
    }

    @Test
    @DisplayName("ADMIN이 계정을 잠그면 기존 JWT와 새 로그인이 차단된다")
    void lockUser_revokesSessionAndBlocksAuthentication() throws Exception {
        // given
        String unique = UUID.randomUUID().toString();
        String password = "Password123!";

        User admin = User.create(
                "관리자",
                "admin-" + unique + "@example.com",
                passwordEncoder.encode(password)
        );
        admin.updateRole(UserRole.ADMIN);
        admin = userRepository.saveAndFlush(admin);
        adminId = admin.getId();

        String targetEmail = "user-" + unique + "@example.com";
        User target = userRepository.saveAndFlush(
                User.create(
                        "일반사용자",
                        targetEmail,
                        passwordEncoder.encode(password)
                )
        );
        targetId = target.getId();

        AuthSession adminSession = authSessionRepository.saveAndFlush(
                AuthSession.create(
                        adminId,
                        clock.instant().plus(Duration.ofDays(1))
                )
        );
        adminSessionId = adminSession.getId();

        AuthSession targetSession = authSessionRepository.saveAndFlush(
                AuthSession.create(
                        targetId,
                        clock.instant().plus(Duration.ofDays(1))
                )
        );
        targetSessionId = targetSession.getId();

        String adminToken = jwtProvider.generateAccessToken(
                adminId, adminSessionId
        );

        String targetToken = jwtProvider.generateAccessToken(
                targetId, targetSessionId
        );

        // when: ADMIN이 계정 잠금
        mockMvc.perform(patch("/api/users/{userId}/lock", targetId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locked": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));

        // then 1: DB에 계정 잠금 반영
        User lockedUser = userRepository.findById(targetId)
                .orElseThrow();

        assertThat(lockedUser.isLocked()).isTrue();

        // then 2: 기존 인증 세션 폐기
        AuthSession revokedSession =
                authSessionRepository.findById(targetSessionId)
                        .orElseThrow();

        assertThat(revokedSession.getRevokedAt()).isNotNull();

        // then 3: 기존 JWT 사용 불가
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + targetToken)
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isUnauthorized());

        // then 4: 잠긴 계정은 새 로그인 불가
        assertThatThrownBy(() ->
                authService.authenticate(targetEmail, password)
        ).isInstanceOf(AccountLockedException.class);
    }
}
