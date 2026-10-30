package codeit.ootd.domain.user.service;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.dto.response.UserListResponse;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.impl.UserServiceImpl;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("회원가입 중 이메일 UNIQUE 제약조건 위반 시 중복 이메일 예외가 발생한다")
    void signup_emailUniqueViolation_throwsDuplicateEmailException() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "password123!"
        );

        when(passwordEncoder.encode(request.password()))
                .thenReturn("encoded-password");

        ConstraintViolationException cause =
                new ConstraintViolationException(
                        "이메일 UNIQUE 제약조건 위반",
                        new SQLException(
                                "duplicate key",
                                "23505"
                        ),
                        "uk_users_email"
                );

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "이메일 중복",
                        cause
                );

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(exception);

        // when & then
        assertThrows(
                DuplicateEmailException.class,
                () -> userService.signup(request)
        );
        verify(userRepository).existsByEmail(request.email());
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("회원가입 성공 시 비밀번호를 암호화하고 사용자를 저장한다")
    void signup_success() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트 사용자",
                "test@example.com",
                "password123!"
        );

        given(passwordEncoder.encode(request.password()))
                .willReturn("encodedPassword");

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserDto response = userService.signup(request);

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(passwordEncoder).encode(request.password());
        verify(userRepository).saveAndFlush(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertAll(
                () -> assertEquals(request.name(), savedUser.getName()),
                () -> assertEquals(request.email(), savedUser.getEmail()),
                () -> assertEquals("encodedPassword", savedUser.getPassword()),
                () -> assertEquals(UserRole.USER, savedUser.getRole()),
                () -> assertFalse(savedUser.isLocked()),
                () -> assertEquals(UserStatus.ACTIVE, savedUser.getStatus()),
                () -> assertNull(savedUser.getDeletedAt()),

                // UserDto 반환값 확인
                () -> assertEquals(savedUser.getEmail(), response.email()),
                () -> assertEquals(savedUser.getName(), response.name()),
                () -> assertEquals(savedUser.getRole(), response.role()),
                () -> assertEquals(savedUser.isLocked(), response.locked())
        );
    }

    @Test
    @DisplayName("이메일 UNIQUE 이외의 DB 제약조건 위반은 원래 예외를 전달한다")
    void signup_otherConstraintViolation_propagatesException() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "password123!"
        );

        when(passwordEncoder.encode(request.password()))
                .thenReturn("encoded-password");

        ConstraintViolationException cause =
                new ConstraintViolationException(
                        "다른 DB 제약조건 위반",
                        new SQLException("check violation", "23514"),
                        "chk_users_role"
                );

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "DB 제약조건 오류",
                        cause
                );

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(exception);

        // when & then
        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> userService.signup(request)
        );

        assertSame(exception, thrown);
    }

    @Test
    @DisplayName("다른 UNIQUE 제약조건 위반은 이메일 중복 예외로 변환하지 않는다")
    void signup_otherUniqueViolation_propagatesException() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "password123!"
        );

        when(passwordEncoder.encode(request.password()))
                .thenReturn("encoded-password");

        ConstraintViolationException cause =
                new ConstraintViolationException(
                        "다른 UNIQUE 제약조건 위반",
                        new SQLException("duplicate key", "23505"),
                        "uk_other_constraint"
                );

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "DB UNIQUE 제약조건 오류",
                        cause
                );

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(exception);

        // when & then
        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> userService.signup(request)
        );

        assertSame(exception, thrown);
    }

    @Test
    @DisplayName("이메일 UNIQUE 제약조건 이름이어도 SQLSTATE가 다르면 원래 예외를 전달한다")
    void signup_emailConstraintWithDifferentSqlState_propagatesException() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "password123!"
        );

        when(passwordEncoder.encode(request.password()))
                .thenReturn("encoded-password");

        ConstraintViolationException cause =
                new ConstraintViolationException(
                        "다른 SQLSTATE 오류",
                        new SQLException("check violation", "23514"),
                        "uk_users_email"
                );

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("DB 제약조건 오류", cause);

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(exception);

        // when & then
        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> userService.signup(request)
        );

        assertSame(exception, thrown);
    }

    @Test
    @DisplayName("사용자 목록 첫 페이지 조회 시 이메일 오름차순으로 반환한다")
    void getUsers_firstPage_success() {
        // given
        User user1 = User.create(
                "사용자A", "aaa@example.com", "encoded-password"
        );
        User user2 = User.create(
                "사용자B", "bbb@example.com", "encoded-password"
        );
        User user3 = User.create(
                "사용자C", "ccc@example.com", "encoded-password"
        );

        List<User> users = List.of(user1, user2, user3);

        Page<User> page = new PageImpl<>(
                users,
                PageRequest.of(
                        0,
                        10,
                        Sort.by(Sort.Direction.ASC, "email")
                ),
                3
        );

        when(userRepository.findAll(any(
                org.springframework.data.domain.Pageable.class
        ))).thenReturn(page);

        // when
        UserListResponse response = userService.getUsers(
                null,
                null,
                10,
                "email",
                "ASCENDING",
                null,
                null,
                null
        );

        // then
        assertAll(
                () -> assertEquals(3, response.data().size()),
                () -> assertEquals("aaa@example.com", response.data().get(0).email()),
                () -> assertEquals("bbb@example.com", response.data().get(1).email()),
                () -> assertEquals("ccc@example.com", response.data().get(2).email()),
                () -> assertFalse(response.hasNext()),
                () -> assertEquals(3L, response.totalCount()),
                () -> assertNull(response.nextCursor()),
                () -> assertNull(response.nextIdAfter()),
                () -> assertEquals("email", response.sortBy()),
                () -> assertEquals("ASCENDING", response.sortDirection())
        );

        verify(userRepository).findAll(any(
                org.springframework.data.domain.Pageable.class
        ));
    }

    @Test
    @DisplayName("다음 페이지가 존재하면 마지막 사용자의 이메일과 ID를 커서로 반환한다")
    void getUsers_firstPage_hasNext_returnsCursor() {
        // given
        User user1 = User.create("사용자A", "aaa@example.com", "encoded-password");
        User user2 = User.create("사용자B", "bbb@example.com", "encoded-password");

        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        ReflectionTestUtils.setField(user1, "id", userId1);
        ReflectionTestUtils.setField(user2, "id", userId2);

        var pageable = PageRequest.of(
                0,
                2,
                Sort.by(Sort.Direction.ASC, "email")
        );

        var page = new PageImpl<>(
                List.of(user1, user2),
                pageable,
                3
        );

        when(userRepository.findAll(pageable)).thenReturn(page);

        // when
        UserListResponse response = userService.getUsers(
                null,
                null,
                2,
                "email",
                "ASCENDING",
                null,
                null,
                null
        );

        // then
        assertAll(
                () -> assertEquals(2, response.data().size()),
                () -> assertTrue(response.hasNext()),
                () -> assertEquals(3L, response.totalCount()),
                () -> assertEquals("bbb@example.com", response.nextCursor()),
                () -> assertEquals(userId2, response.nextIdAfter()),
                () -> assertEquals("email", response.sortBy()),
                () -> assertEquals("ASCENDING", response.sortDirection())
        );

        verify(userRepository).findAll(pageable);
    }


}
