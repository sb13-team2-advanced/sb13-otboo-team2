package codeit.ootd.domain.user.integration;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.UserService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserSignupIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("회원가입 시 사용자 정보가 DB에 저장된다")
    void signup_success() {
        // given
        String email = "test-" + UUID.randomUUID() + "@example.com";
        String rawPassword = "password123!";

        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                email,
                rawPassword
        );

        // when
        UserDto response = userService.signup(request);

        // INSERT SQL을 DB에 반영
        userRepository.flush();

        // 1차 캐시를 초기화해 DB에서 다시 조회
        entityManager.clear();

        // then
        User savedUser = userRepository.findByEmail(email)
                .orElseThrow();

        assertAll(
                // 이름 및 이메일
                () -> assertEquals(
                        request.name(),
                        savedUser.getName()
                ),
                () -> assertEquals(
                        email,
                        savedUser.getEmail()
                ),

                // 비밀번호 암호화
                () -> assertNotEquals(
                        rawPassword,
                        savedUser.getPassword()
                ),
                () -> assertTrue(
                        passwordEncoder.matches(
                                rawPassword,
                                savedUser.getPassword()
                        )
                ),

                // 회원가입 기본 상태
                () -> assertEquals(
                        UserRole.USER,
                        savedUser.getRole()
                ),
                () -> assertEquals(
                        UserStatus.ACTIVE,
                        savedUser.getStatus()
                ),
                () -> assertFalse(
                        savedUser.isLocked()
                ),
                () -> assertNull(
                        savedUser.getDeletedAt()
                ),

                // JPA Auditing
                () -> assertNotNull(
                        savedUser.getCreatedAt()
                ),
                () -> assertNotNull(
                        savedUser.getUpdatedAt()
                ),

                // UserDto 응답 검증
                () -> assertNotNull(
                        response
                ),
                () -> assertEquals(
                        savedUser.getId(), response.id()
                ),
                () -> assertTrue(
                        Duration.between(
                                savedUser.getCreatedAt(),
                                response.createdAt()
                        ).abs().compareTo(Duration.ofMillis(1)) < 0,
                        "DB에 저장된 생성 시간과 DTO 생성 시간의 차이는 1ms 미만이어야 한다"
                ),
                () -> assertEquals(
                        savedUser.getEmail(), response.email()
                ),
                () -> assertEquals(
                        savedUser.getName(), response.name()
                ),
                () -> assertEquals(
                        savedUser.getRole(), response.role()
                ),
                () -> assertEquals(
                        savedUser.isLocked(), response.locked()
                )
        );
    }
    
    @Test
    @DisplayName("이미 가입된 이메일로 회원가입하면 예외가 발생한다.")
    void signup_duplicateEmail() {
        // given
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";

        UserCreateRequest firstRequest = new UserCreateRequest(
                "첫번째사용자",
                email,
                "password123!"
        );

        UserCreateRequest duplicateRequest = new UserCreateRequest(
                "두번째사용자",
                email,
                "password456!"
        );

        userService.signup(firstRequest);
        userRepository.flush();
    
        // when & then
        assertThrows(
                DuplicateEmailException.class,
                () -> userService.signup(duplicateRequest)
        );

        // 첫 번째 사용자만 존재하는지 확인
        assertEquals(1, userRepository.countByEmail(email));
    }
}

