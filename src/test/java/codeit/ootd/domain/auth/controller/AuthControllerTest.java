package codeit.ootd.domain.auth.controller;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.dto.response.TokenRefreshResult;
import codeit.ootd.domain.auth.exception.InvalidRefreshTokenException;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.entity.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("익명 사용자에게 CSRF 토큰과 XSRF-TOKEN 쿠키를 발급한다")
    void csrf_anonymousUser_success() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    @DisplayName("CSRF 쿠키의 토큰을 헤더로 전달하면 로그인 요청에 성공한다")
    void signIn_withCsrfCookieAndHeader_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-28T00:00:00Z");

        UserDto userDto = new UserDto(
                userId,
                createdAt,
                "test@example.com",
                "테스트사용자",
                UserRole.USER,
                false
        );

        SignInResult result = new SignInResult(
                userDto,
                "test-access-token",
                "test-refresh-token"
        );

        when(authService.signIn("test@example.com", "password123!"))
                .thenReturn(result);

        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(multipart("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("username", "test@example.com")
                        .param("password", "password123!"))
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.userDto.id").value(userId.toString()))
                .andExpect(jsonPath("$.userDto.email").value("test@example.com"))
                .andExpect(jsonPath("$.userDto.name").value("테스트사용자"))
                .andExpect(jsonPath("$.userDto.role").value("USER"))
                .andExpect(jsonPath("$.userDto.locked").value(false))
                .andExpect(jsonPath("$.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())

                .andExpect(cookie().value("refreshToken", "test-refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/auth"))
                .andExpect(cookie().maxAge("refreshToken", 604800))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("SameSite=Lax")
                ));

        verify(authService).signIn(
                "test@example.com",
                "password123!"
        );
    }

    @Test
    @DisplayName("프론트 CSRF 경로로 토큰을 발급한다")
    void csrf_frontendPath_success() throws Exception {
        mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    @DisplayName("CSRF 헤더가 없으면 로그인 요청을 거부한다")
    void signIn_withoutCsrfHeader_forbidden() throws Exception {
        // given
        Cookie csrfCookie = issueCsrfCookie();

        // when & then
        mockMvc.perform(multipart("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .param("username", "test@example.com")
                        .param("password", "password123!"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("잘못된 CSRF 토큰으로 로그인 요청하면 403을 반환한다")
    void signIn_invalidCsrfToken_forbidden() throws Exception {
        // given
        Cookie csrfCookie = issueCsrfCookie();

        // when & then
        mockMvc.perform(multipart("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "invalid-csrf-token")
                        .param("username", "test@example.com")
                        .param("password", "password123!"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("로그인 시 이메일 형식이 올바르지 않으면 400을 반환한다")
    void signIn_invalidEmail_badRequest() throws Exception {
        // given
        Cookie csrfCookie = issueCsrfCookie();

        // when & then
        mockMvc.perform(multipart("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("username", "invalid-email")
                        .param("password", "password123!"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("로그인 시 username이 누락되면 400을 반환한다")
    void signIn_missingUsername_badRequest() throws Exception {
        // given
        Cookie csrfCookie = issueCsrfCookie();

        // when & then
        mockMvc.perform(multipart("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("password", "password123!"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("로그인 시 비밀번호가 누락되면 400을 반환한다")
    void signIn_missingPassword_badRequest() throws Exception {
        // given
        Cookie csrfCookie = issueCsrfCookie();

        // when & then
        mockMvc.perform(multipart("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("username", "test@example.com"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("유효한 Refresh Token 쿠키로 토큰 재발급에 성공한다")
    void refresh_success() throws Exception {
        // given
        String oldRefreshToken = "old-refresh-token";

        TokenRefreshResult result = new TokenRefreshResult(
                "new-access-token",
                "new-refresh-token"
        );

        when(authService.refresh(oldRefreshToken))
                .thenReturn(result);

        Cookie csrfCookie = issueCsrfCookie();

        Cookie refreshCookie = new Cookie(
                "refreshToken",
                oldRefreshToken
        );

        // when & then
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrfCookie, refreshCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value(
                        "refreshToken",
                        "new-refresh-token"
                ))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/auth"));

        verify(authService).refresh(oldRefreshToken);
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 재발급 요청을 거부한다")
    void refresh_missingCookie() throws Exception {
        // given
        Cookie csrfCookie = issueCsrfCookie();

        // when & then
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(anyString());
    }

    @Test
    @DisplayName("CSRF 헤더가 없으면 Refresh Token 재발급을 거부한다")
    void refresh_withoutCsrfHeader_forbidden() throws Exception {
        // given
        Cookie csrfCookie = issueCsrfCookie();

        Cookie refreshCookie = new Cookie(
                "refreshToken",
                "old-refresh-token"
        );

        // when & then
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrfCookie, refreshCookie))
                .andExpect(status().isForbidden());

        // CSRF 검증에 실패하면 서비스는 호출되지 않아야 함
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("잘못된 CSRF 토큰으로 Refresh Token 재발급 요청 시 403을 반환한다")
    void refresh_invalidCsrfToken_forbidden() throws Exception {
        // given
        Cookie csrfCookie = issueCsrfCookie();

        Cookie refreshCookie = new Cookie(
                "refreshToken",
                "old-refresh-token"
        );

        // when & then
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrfCookie, refreshCookie)
                        .header("X-XSRF-TOKEN", "invalid-csrf-token"))
                .andExpect(status().isForbidden());

        // CSRF 검증에 실패하면 재발급 서비스가 호출되지 않아야 함
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token으로 재발급 요청 시 401을 반환한다")
    void refresh_invalidToken_unauthorized() throws Exception {
        // given
        String invalidRefreshToken = "invalid-refresh-token";

        Cookie csrfCookie = issueCsrfCookie();

        when(authService.refresh(invalidRefreshToken))
                .thenThrow(new InvalidRefreshTokenException());

        // when & then
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(
                                csrfCookie,
                                new Cookie("refreshToken", invalidRefreshToken)
                        )
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isUnauthorized());

        verify(authService).refresh(invalidRefreshToken);
    }

    @Test
    @DisplayName("로그아웃 성공 시 204를 반환하고 Refresh Token 쿠키를 삭제한다")
    void signOut_success() throws Exception {
        // given
        String refreshToken = "valid-refresh-token";

        Cookie csrfCookie = issueCsrfCookie();

        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);

        // when & then
        mockMvc.perform(post("/api/auth/sign-out")
                        .cookie(csrfCookie, refreshCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/auth"));

        verify(authService).signOut(refreshToken);
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없어도 로그아웃에 성공한다")
    void signOut_withoutRefreshToken() throws Exception {
        // given
        // 기존 CSRF 쿠키 발급 코드 대신
        Cookie csrfCookie = issueCsrfCookie();

        // when & then
        mockMvc.perform(post("/api/auth/sign-out")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().path("refreshToken", "/api/auth"));

        verify(authService, never()).signOut(anyString());
    }


    private Cookie issueCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        return csrfCookie;
    }


}
