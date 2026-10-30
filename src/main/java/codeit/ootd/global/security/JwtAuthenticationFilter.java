package codeit.ootd.global.security;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.AccountInactiveException;
import codeit.ootd.domain.auth.exception.AccountLockedException;
import codeit.ootd.domain.auth.exception.AuthenticationRequiredException;
import codeit.ootd.domain.auth.exception.InvalidAuthSessionException;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.ErrorResponse;
import codeit.ootd.global.exception.OtbooException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final AuthSessionService authSessionService;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && "/api/auth/refresh".equals(
                request.getServletPath()
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);

        if (token.isBlank()) {
            SecurityContextHolder.clearContext();

            writeAuthenticationError(
                    response,
                    ErrorCode.AUTHENTICATION_REQUIRED
            );
            return;
        }

        try {
            Claims claims = jwtProvider.parseAccessToken(token);

            UUID userId;
            UUID sessionId;

            try {
                userId = UUID.fromString(claims.getSubject());
                sessionId = UUID.fromString(
                        claims.get("sessionId", String.class)
                );
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new AuthenticationRequiredException();
            }

            AuthSession session = authSessionService.getActiveSession(sessionId);

            if (!session.getUserId().equals(userId)) {
                throw new AuthenticationRequiredException();
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(AuthenticationRequiredException::new);

            if (user.isLocked()) {
                throw new AccountLockedException();
            }

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new AccountInactiveException();
            }

            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );

            var authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    authorities
            );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

        } catch (AccountLockedException e) {
        SecurityContextHolder.clearContext();

        writeAuthenticationError(
                response,
                ErrorCode.ACCOUNT_LOCKED
            );
            return;

        } catch (AccountInactiveException e) {
        SecurityContextHolder.clearContext();

        writeAuthenticationError(
                response,
                ErrorCode.ACCOUNT_INACTIVE
            );
            return;

        } catch (InvalidAuthSessionException e) {
            SecurityContextHolder.clearContext();

            writeAuthenticationError(
                    response,
                    ErrorCode.INVALID_AUTH_SESSION
            );
            return;

        } catch (AuthenticationRequiredException e) {
            SecurityContextHolder.clearContext();

            writeAuthenticationError(
                    response,
                    ErrorCode.AUTHENTICATION_REQUIRED
            );
            return;

        } catch (JwtException e) {
            SecurityContextHolder.clearContext();

            writeAuthenticationError(
                    response,
                    ErrorCode.AUTHENTICATION_REQUIRED
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeAuthenticationError(
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {

        OtbooException exception = new OtbooException(errorCode);
        ErrorResponse errorResponse =
                ErrorResponse.from(exception, errorCode);

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        jsonMapper.writeValue(
                response.getWriter(),
                errorResponse
        );
    }

}
