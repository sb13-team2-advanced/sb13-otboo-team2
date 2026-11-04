package codeit.ootd.domain.user.init;

import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(userRepository.existsByEmail(ADMIN_EMAIL)).thenReturn(false);
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
        when(userRepository.existsByEmail(ADMIN_EMAIL)).thenReturn(true);

        // when
        adminInitializer.initialize();

        // then
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }
}
