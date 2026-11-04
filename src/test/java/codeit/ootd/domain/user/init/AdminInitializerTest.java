package codeit.ootd.domain.user.init;

import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminInitializer adminInitializer;

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "TestPassword123!";

    @BeforeEach
    void setUp() {
        adminInitializer = new AdminInitializer(
                userRepository,
                passwordEncoder,
                "관리자",
                ADMIN_EMAIL,
                ADMIN_PASSWORD
        );
    }

    @Test
    @DisplayName("초기 관리자 계정이 없으면 ADMIN 권한으로 생성한다")
    void initialize_adminNotExists_createsAdmin() {
        // given
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode(ADMIN_PASSWORD))
                .thenReturn("encoded-password");

        // when
        adminInitializer.initialize();

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedAdmin = captor.getValue();

        assertThat(savedAdmin.getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(savedAdmin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(savedAdmin.getPassword()).isEqualTo("encoded-password");
        assertThat(savedAdmin.isLocked()).isFalse();
    }

    @Test
    @DisplayName("초기 관리자 계정이 이미 존재하면 중복 생성하지 않는다")
    void initialize_adminExists_doesNotCreateAdmin() {
        // given
        User existingAdmin = mock(User.class);

        when(existingAdmin.getRole()).thenReturn(UserRole.ADMIN);
        when(existingAdmin.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(existingAdmin.isLocked()).thenReturn(false);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(existingAdmin));

        // when
        adminInitializer.initialize();

        // then
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("초기 관리자 이메일이 일반 사용자에게 사용 중이면 초기화에 실패한다")
    void initialize_emailBelongsToUser_throwsException() {
        // given
        User existingUser = mock(User.class);

        when(existingUser.getRole()).thenReturn(UserRole.USER);
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(existingUser));

        // when & then
        assertThatThrownBy(() -> adminInitializer.initialize())
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("초기 관리자 비밀번호가 설정되지 않으면 초기화에 실패한다")
    void initialize_missingPassword_throwsException() {
        // given
        AdminInitializer initializer = new AdminInitializer(
                userRepository,
                passwordEncoder,
                "관리자",
                ADMIN_EMAIL,
                ""
        );

        // when & then
        assertThatThrownBy(initializer::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("초기 관리자 이메일과 비밀번호 설정이 필요합니다.");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("기존 관리자 계정이 잠겨 있으면 초기화에 실패한다")
    void initialize_lockedAdmin_throwsException() {
        // given
        User existingAdmin = mock(User.class);

        when(existingAdmin.getRole()).thenReturn(UserRole.ADMIN);
        when(existingAdmin.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(existingAdmin.isLocked()).thenReturn(true);
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(existingAdmin));

        // when & then
        assertThatThrownBy(adminInitializer::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("초기 관리자 이메일에 사용 가능한 ADMIN 계정이 존재하지 않습니다.");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }


}
