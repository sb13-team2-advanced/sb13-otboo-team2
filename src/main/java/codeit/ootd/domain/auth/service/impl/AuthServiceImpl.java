package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.AccountInactiveException;
import codeit.ootd.domain.auth.exception.AccountLockedException;
import codeit.ootd.domain.auth.exception.InvalidCredentialsException;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.auth.service.RefreshTokenService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
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

        return new SignInResult(accessToken, refreshToken);
    }

}
