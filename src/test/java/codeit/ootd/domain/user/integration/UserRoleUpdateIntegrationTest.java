package codeit.ootd.domain.user.integration;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
class UserRoleUpdateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private Clock clock;

    private UUID adminUserId;
    private UUID targetUserId;
    private UUID adminSessionId;
    private UUID targetSessionId;

    @AfterEach
    void tearDown() {
        // FK 제약조건을 고려해 세션부터 삭제
        if (adminSessionId != null) {
            authSessionRepository.deleteById(adminSessionId);
        }
        if (targetSessionId != null) {
            authSessionRepository.deleteById(targetSessionId);
        }
        if (adminUserId != null) {
            userRepository.deleteById(adminUserId);
        }
        if (targetUserId != null) {
            userRepository.deleteById(targetUserId);
        }
    }

    @Test
    @DisplayName("ADMIN 권한 변경 시 DB에 반영되고 대상 사용자의 기존 JWT가 무효화된다")
    void updateRole_revokesTargetSessionAndRejectsOldToken() throws Exception {
        // given
        String unique = UUID.randomUUID().toString();
        Instant now = clock.instant();

        User admin = User.create(
                "관리자",
                "admin-" + unique + "@example.com",
                "encoded-password"
        );
        admin.updateRole(UserRole.ADMIN);
        admin = userRepository.saveAndFlush(admin);
        adminUserId = admin.getId();

        User target = User.create(
                "일반사용자",
                "user-" + unique + "@example.com",
                "encoded-password"
        );
        target = userRepository.saveAndFlush(target);
        targetUserId = target.getId();

        AuthSession adminSession = authSessionRepository.saveAndFlush(
                AuthSession.create(
                        adminUserId,
                        now.plus(Duration.ofDays(1))
                )
        );
        adminSessionId = adminSession.getId();

        AuthSession targetSession = authSessionRepository.saveAndFlush(
                AuthSession.create(
                        targetUserId,
                        now.plus(Duration.ofDays(1))
                )
        );
        targetSessionId = targetSession.getId();

        String adminToken = jwtProvider.generateAccessToken(
                adminUserId, adminSessionId
        );

        String targetToken = jwtProvider.generateAccessToken(
                targetUserId, targetSessionId
        );

        // when: 관리자가 대상 사용자의 권한 변경
        mockMvc.perform(patch("/api/users/{userId}/role", targetUserId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(targetUserId.toString()))
                .andExpect(jsonPath("$.role")
                        .value("ADMIN"));

        // then 1: 사용자 권한이 DB에 반영됨
        User updatedUser = userRepository.findById(targetUserId)
                .orElseThrow();

        assertEquals(UserRole.ADMIN, updatedUser.getRole());

        // then 2: 대상 사용자의 세션이 폐기됨
        AuthSession revokedSession =
                authSessionRepository.findById(targetSessionId)
                        .orElseThrow();

        assertNotNull(revokedSession.getRevokedAt());
        assertFalse(revokedSession.isActive(clock.instant()));

        // then 3: 기존 JWT로 다시 접근하면 401
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + targetToken)
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isUnauthorized());

        // then 4: 관리자 세션은 영향을 받지 않음
        AuthSession activeAdminSession =
                authSessionRepository.findById(adminSessionId)
                        .orElseThrow();

        assertTrue(activeAdminSession.isActive(clock.instant()));
    }
}
