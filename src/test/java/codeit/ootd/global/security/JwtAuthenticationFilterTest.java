package codeit.ootd.global.security;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.InvalidAuthSessionException;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Spy
    private JsonMapper jsonMapper = new JsonMapper();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 JWT와 활성 세션으로 요청하면 인증 정보를 등록한다")
    void doFilter_validToken_authenticates() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String accessToken = "valid-access-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("sessionId", String.class))
                .thenReturn(sessionId.toString());

        when(jwtProvider.parseAccessToken(accessToken))
                .thenReturn(claims);

        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(userId);
        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        User user = mock(User.class);
        when(user.getRole()).thenReturn(UserRole.USER);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());

        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_USER")));

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("JWT 사용자와 인증 세션 소유자가 다르면 인증을 거부한다")
    void doFilter_sessionOwnerMismatch_rejected() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();

        String accessToken = "valid-access-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("sessionId", String.class))
                .thenReturn(sessionId.toString());

        when(jwtProvider.parseAccessToken(accessToken))
                .thenReturn(claims);

        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(anotherUserId);

        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertEquals(401, response.getStatus());

        String responseBody = response.getContentAsString();

        assertTrue(responseBody.contains(
                "\"message\":\"인증이 필요합니다.\""
        ));

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(userRepository);
        verify(filterChain, never()).doFilter(any(), any());

    }

    @Test
    @DisplayName("잠긴 사용자의 JWT로 요청하면 인증을 거부한다")
    void doFilter_lockedUser_rejected() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String accessToken = "valid-access-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("sessionId", String.class))
                .thenReturn(sessionId.toString());

        when(jwtProvider.parseAccessToken(accessToken))
                .thenReturn(claims);

        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(userId);
        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        User user = mock(User.class);
        when(user.isLocked()).thenReturn(true);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertEquals(403, response.getStatus());

        String responseBody = response.getContentAsString();

        assertTrue(responseBody.contains(
                "\"message\":\"잠긴 계정입니다.\""
        ));

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(filterChain, never()).doFilter(any(), any());

    }

    @Test
    @DisplayName("비활성 사용자의 JWT로 요청하면 인증을 거부한다")
    void doFilter_inactiveUser_rejected() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String accessToken = "valid-access-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("sessionId", String.class))
                .thenReturn(sessionId.toString());

        when(jwtProvider.parseAccessToken(accessToken))
                .thenReturn(claims);

        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(userId);
        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.DEACTIVATED);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertEquals(403, response.getStatus());

        String responseBody = response.getContentAsString();

        assertTrue(responseBody.contains(
                "\"message\":\"활성화되지 않은 계정입니다.\""
        ));

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(filterChain, never()).doFilter(any(), any());

    }

    @Test
    @DisplayName("서명이 유효하지 않은 Access Token으로 요청하면 401을 반환한다")
    void doFilter_invalidSignature_rejected() throws Exception {
        // given
        String accessToken = "invalid-signature-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtProvider.parseAccessToken(accessToken))
                .thenThrow(new SignatureException("JWT 서명이 유효하지 않습니다."));

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertEquals(401, response.getStatus());

        String responseBody = response.getContentAsString();

        assertTrue(responseBody.contains("\"exceptionName\""));
        assertTrue(responseBody.contains("\"message\":\"인증이 필요합니다.\""));

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(authSessionService, userRepository);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("만료된 Access Token으로 요청하면 401과 오류 응답을 반환한다")
    void doFilter_expiredToken_returnsUnauthorized() throws Exception {
        // given
        String accessToken = "expired-access-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtProvider.parseAccessToken(accessToken))
                .thenThrow(new ExpiredJwtException(
                        null,
                        null,
                        "Access Token이 만료되었습니다."
                ));

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertEquals(401, response.getStatus());
        assertTrue(
                response.getContentType().startsWith("application/json")
        );
        String responseBody = response.getContentAsString();

        assertTrue(responseBody.contains("\"exceptionName\""));
        assertTrue(responseBody.contains("\"message\":\"인증이 필요합니다.\""));

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(authSessionService, userRepository);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("폐기된 인증 세션의 JWT로 요청하면 401을 반환한다")
    void doFilter_revokedSession_returnsUnauthorized() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String accessToken = "valid-access-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("sessionId", String.class))
                .thenReturn(sessionId.toString());
        when(jwtProvider.parseAccessToken(accessToken))
                .thenReturn(claims);

        when(authSessionService.getActiveSession(sessionId))
                .thenThrow(new InvalidAuthSessionException());

        // when
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // then
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString()
                .contains("유효하지 않거나 만료된 인증 세션입니다."));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userRepository);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증 없이 다음 필터로 전달한다")
    void doFilter_withoutAuthorizationHeader_continues() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(
                jwtProvider,
                authSessionService,
                userRepository
        );

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("JWT 사용자 ID가 UUID 형식이 아니면 401을 반환한다")
    void doFilter_invalidUserId_returnsUnauthorized() throws Exception {
        // given
        String accessToken = "invalid-user-id-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("invalid-user-id");

        when(jwtProvider.parseAccessToken(accessToken))
                .thenReturn(claims);

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertEquals(401, response.getStatus());

        assertTrue(response.getContentAsString().contains(
                "\"message\":\"인증이 필요합니다.\""
        ));

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(authSessionService, userRepository);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("JWT 사용자가 존재하지 않으면 401을 반환한다")
    void doFilter_userNotFound_returnsUnauthorized() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String accessToken = "valid-access-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("sessionId", String.class))
                .thenReturn(sessionId.toString());

        when(jwtProvider.parseAccessToken(accessToken))
                .thenReturn(claims);

        AuthSession session = mock(AuthSession.class);
        when(session.getUserId()).thenReturn(userId);
        when(authSessionService.getActiveSession(sessionId))
                .thenReturn(session);

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertEquals(401, response.getStatus());

        assertTrue(response.getContentAsString().contains(
                "\"message\":\"인증이 필요합니다.\""
        ));

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(userRepository).findById(userId);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("JWT에 sessionId가 없으면 401을 반환한다")
    void doFilter_missingSessionId_returnsUnauthorized() throws Exception {
        // given
        String accessToken = "missing-session-id-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(UUID.randomUUID().toString());

        when(jwtProvider.parseAccessToken(accessToken))
                .thenReturn(claims);

        // when
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // then
        assertEquals(401, response.getStatus());

        assertTrue(response.getContentAsString().contains(
                "\"message\":\"인증이 필요합니다.\""
        ));

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(authSessionService, userRepository);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Bearer Token이 비어 있으면 401을 반환한다")
    void doFilter_emptyBearerToken_returnsUnauthorized() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");

        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        assertEquals(401, response.getStatus());

        assertTrue(response.getContentAsString().contains(
                "\"message\":\"인증이 필요합니다.\""
        ));

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(
                jwtProvider,
                authSessionService,
                userRepository
        );

        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("재발급 요청은 만료된 Access Token이 있어도 JWT 필터를 건너뛴다")
    void doFilter_refreshRequest_skipsJwtAuthentication() throws Exception {
        // given
        String accessToken = "expired-access-token";

        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setServletPath("/api/auth/refresh");
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        verifyNoInteractions(
                jwtProvider,
                authSessionService,
                userRepository
        );

        verify(filterChain).doFilter(request, response);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );
    }

}
