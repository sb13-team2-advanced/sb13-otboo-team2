package codeit.ootd.domain.user.integration;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.user.entity.User;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
class UserPasswordIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired AuthSessionRepository authSessionRepository;
    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock clock;

    private UUID userId;
    private UUID sessionId;

    @AfterEach
    void tearDown() {
        if (sessionId != null) {
            authSessionRepository.deleteById(sessionId);
        }
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    @DisplayName("본인이 비밀번호를 변경하면 기존 JWT가 무효화되고 새 비밀번호로 인증된다")
    void updatePassword_revokesSessionAndUpdatesPassword() throws Exception {
        // given
        String unique = UUID.randomUUID().toString();
        String email = "password-" + unique + "@example.com";
        String oldPassword = "OldPassword123!";
        String newPassword = "NewPassword123!";

        User user = userRepository.saveAndFlush(
                User.create(
                        "테스트사용자",
                        email,
                        passwordEncoder.encode(oldPassword)
                )
        );
        userId = user.getId();

        AuthSession session = authSessionRepository.saveAndFlush(
                AuthSession.create(
                        userId,
                        clock.instant().plus(Duration.ofDays(1))
                )
        );
        sessionId = session.getId();

        String accessToken = jwtProvider.generateAccessToken(
                userId,
                sessionId
        );

        mockMvc.perform(patch("/api/users/{userId}/password", userId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "NewPassword123!"}
                                """))
                .andExpect(status().isNoContent());

        User updatedUser = userRepository.findById(userId)
                .orElseThrow();

        assertThat(updatedUser.getPassword())
                .isNotEqualTo(newPassword);

        assertThat(passwordEncoder.matches(
                newPassword,
                updatedUser.getPassword()
        )).isTrue();

        assertThat(passwordEncoder.matches(
                oldPassword,
                updatedUser.getPassword()
        )).isFalse();

        AuthSession revokedSession = authSessionRepository
                .findById(sessionId)
                .orElseThrow();

        assertThat(revokedSession.getRevokedAt()).isNotNull();

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("limit", "10")
                        .param("sortBy", "email")
                        .param("sortDirection", "ASCENDING"))
                .andExpect(status().isUnauthorized());

        User authenticatedUser = authService.authenticate(
                email,
                newPassword
        );

        assertThat(authenticatedUser.getId()).isEqualTo(userId);
    }
}

