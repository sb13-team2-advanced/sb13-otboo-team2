package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.exception.AccountInactiveException;
import codeit.ootd.domain.auth.exception.AccountLockedException;
import codeit.ootd.domain.auth.exception.InvalidCredentialsException;
import codeit.ootd.domain.auth.service.impl.AuthServiceImpl;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("이메일과 비밀번호가 일치하면 인증에 성공한다")
    void authenticate_success() {
        // given
        String email = "test@example.com";
        String rawPassword = "password123!";
        String encodedPassword = "encoded-password";

        User user = createUser(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                rawPassword,
                encodedPassword
        )).thenReturn(true);

        // when
        User result = authService.authenticate(
                email,
                rawPassword
        );

        // then
        assertSame(user, result);

        verify(userRepository).findByEmail(email);
        verify(passwordEncoder).matches(
                rawPassword,
                encodedPassword
        );
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인하면 인증에 실패한다")
    void authenticate_emailNotFound_throwsException() {
        // given
        String email = "unknown@example.com";
        String password = "password123!";

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate(email, password)
        );

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 인증에 실패한다")
    void authenticate_wrongPassword_throwsException() {
        // given
        String email = "test@example.com";
        String password = "wrongPassword";

        User user = createUser(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(password, user.getPassword()))
                .thenReturn(false);

        // when & then
        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate(email, password)
        );

        verify(passwordEncoder).matches(
                password,
                user.getPassword()
        );
    }

    @Test
    @DisplayName("잠긴 계정은 비밀번호가 일치해도 로그인할 수 없다")
    void authenticate_lockedUser_throwsException() {
        // given
        String email = "test@example.com";
        String password = "password123!";

        User user = createUser(email);
        user.lock();

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(password, user.getPassword()))
                .thenReturn(true);

        // when & then
        assertThrows(
                AccountLockedException.class,
                () -> authService.authenticate(email, password)
        );
    }

    private User createUser(String email) {
        return User.create(
                "테스트사용자",
                email,
                "encoded-password"
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"DEACTIVATED", "DELETED"}
    )
    @DisplayName("비활성화되거나 탈퇴한 계정은 로그인할 수 없다")
    void authenticate_inactiveUser_throwsException(UserStatus status) {
        // given
        String email = "test@example.com";
        String password = "password123!";

        User user = createUser(email);

        if (status == UserStatus.DEACTIVATED) {
            user.deactivate();
        } else {
            user.markDeleted();
        }

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(password, user.getPassword()))
                .thenReturn(true);

        // when & then
        assertThrows(
                AccountInactiveException.class,
                () -> authService.authenticate(email, password)
        );
    }
}
