package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.dto.response.TokenRefreshResult;
import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.AccountInactiveException;
import codeit.ootd.domain.auth.exception.AccountLockedException;
import codeit.ootd.domain.auth.exception.InvalidAuthSessionException;
import codeit.ootd.domain.auth.exception.InvalidCredentialsException;
import codeit.ootd.domain.auth.exception.InvalidRefreshTokenException;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.auth.service.RefreshTokenService;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;

    @Override
    public User authenticate(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (user.isLocked()) {
            throw new AccountLockedException();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountInactiveException();
        }

        return user;
    }

    @Override
    @Transactional
    public SignInResult signIn(String email, String password) {
        User user = authenticate(email, password);

        AuthSession session = authSessionService.createSession(user.getId());

        String accessToken = jwtProvider.generateAccessToken(
                user.getId(),
                session.getId()
        );

        String refreshToken = refreshTokenService.issue(session.getId());

        return new SignInResult(
                UserDto.from(user),
                accessToken,
                refreshToken
        );

    }

    @Override
    @Transactional
    public TokenRefreshResult refresh(String refreshToken) {

        UUID sessionId = refreshTokenService.findSessionIdByToken(refreshToken);

        AuthSession session;

        try {
            session = authSessionService.getActiveSession(sessionId);
        } catch (InvalidAuthSessionException e) {
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() ->
                        new IllegalArgumentException("사용자가 존재하지 않습니다.")
                );

        if (user.isLocked()) {
            throw new AccountLockedException();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountInactiveException();
        }

        String newRefreshToken = refreshTokenService.rotate(
                sessionId,
                refreshToken
        );

        String newAccessToken = jwtProvider.generateAccessToken(
                user.getId(),
                sessionId
        );

        return new TokenRefreshResult(
                newAccessToken,
                newRefreshToken
        );
    }

    @Override
    @Transactional
    public void signOut(String refreshToken) {
        try {
            UUID sessionId = refreshTokenService.findSessionIdByToken(refreshToken);

            authSessionService.revokeSession(sessionId);

        } catch (InvalidRefreshTokenException | InvalidAuthSessionException e) {
            log.debug("로그아웃 요청: 이미 유효하지 않은 토큰 또는 인증 세션");
        }
    }
}
