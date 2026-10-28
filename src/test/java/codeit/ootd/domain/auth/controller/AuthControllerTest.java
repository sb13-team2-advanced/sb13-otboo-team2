package codeit.ootd.domain.auth.controller;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.entity.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andReturn();

        Cookie csrfCookie = csrfResult.getResponse()
                .getCookie("XSRF-TOKEN");

        assertNotNull(csrfCookie);

        mockMvc.perform(post("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "test@example.com",
                              "password": "password123!"
                            }
                            """))
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
                        org.hamcrest.Matchers.containsString("SameSite=Lax")
                ));

        verify(authService).signIn(
                "test@example.com",
                "password123!"
        );
    }

    @Test
    @DisplayName("CSRF 헤더가 없으면 로그인 요청을 거부한다")
    void signIn_withoutCsrfHeader_forbidden() throws Exception {
        // given
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie csrfCookie = csrfResult.getResponse()
                .getCookie("XSRF-TOKEN");

        assertNotNull(csrfCookie);

        // when & then
        mockMvc.perform(post("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "test@example.com",
                              "password": "password123!"
                            }
                            """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("잘못된 CSRF 토큰으로 로그인 요청하면 403을 반환한다")
    void signIn_invalidCsrfToken_forbidden() throws Exception {
        // given
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie csrfCookie = csrfResult.getResponse()
                .getCookie("XSRF-TOKEN");

        assertNotNull(csrfCookie);

        // when & then
        mockMvc.perform(post("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "invalid-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "test@example.com",
                              "password": "password123!"
                            }
                            """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

}
