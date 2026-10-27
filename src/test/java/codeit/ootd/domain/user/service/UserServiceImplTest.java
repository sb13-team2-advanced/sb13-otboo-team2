package codeit.ootd.domain.user.service;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("이미 가입된 이메일이면 회원가입에 실패한다")
    void signup_duplicateEmail_throwsException() {

        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트 사용자",
                "test@example.com",
                "password123!"
        );

        given(userRepository.existsByEmail(request.email()))
                .willReturn(true);

        // when & then
        assertThrows(
                DuplicateEmailException.class,
                () -> userService.signup(request)
        );

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("회원가입 성공 시 비밀번호를 암호화하고 사용자를 저장한다.")
    void signup_success() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트 사용자",
                "test@example.com",
                "password123!"
        );

        given(userRepository.existsByEmail(request.email()))
                .willReturn(false);

        given(passwordEncoder.encode(request.password()))
                .willReturn("encodedPassword");

        // when
        userService.signup(request);

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(passwordEncoder).encode(request.password());
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals(request.name(), savedUser.getName());
        assertEquals(request.email(), savedUser.getEmail());
        assertEquals("encodedPassword", savedUser.getPassword());
        assertEquals(UserRole.USER, savedUser.getRole());
        assertFalse(savedUser.isLocked());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertNull(savedUser.getDeletedAt());

    }
}
