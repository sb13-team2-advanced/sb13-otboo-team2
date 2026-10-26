package codeit.ootd.domain.user.service;

import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("이미 가입된 이메일이면 회원가입에 실패한다")
    void signup_duplicateEmail_throwsException() {

        // given
        String email = "test@example.com";

        given(userRepository.existsByEmail(email))
                .willReturn(true);

        // when & then
        assertThrows(
                DuplicateEmailException.class,
                () -> userService.signup(email)
        );
    }
}
