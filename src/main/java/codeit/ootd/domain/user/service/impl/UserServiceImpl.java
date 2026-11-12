package codeit.ootd.domain.user.service.impl;

import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.dto.response.UserListResponse;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.exception.UserNotFoundException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    @Override
    @Transactional
    public UserDto signup(UserCreateRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            log.debug("회원가입 실패: 이메일 중복");
            throw new DuplicateEmailException();
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = User.create(
                request.name(),
                request.email(),
                encodedPassword
        );

        try {
            User savedUser = userRepository.saveAndFlush(user);

            log.info("회원가입 사용자 저장 처리 완료");

            return UserDto.from(savedUser);

        } catch (DataIntegrityViolationException e) {

            if (isEmailUniqueViolation(e)) {
                log.debug("회원가입 실패: 이메일 UNIQUE 제약조건 위반");
                throw new DuplicateEmailException();
            }

            throw e;
        }
    }

    private boolean isEmailUniqueViolation(Throwable throwable) {
        Throwable cause = throwable;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException exception
                    && "uk_users_email".equalsIgnoreCase(exception.getConstraintName())
                    && "23505".equals(exception.getSQLState())) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public UserListResponse getUsers(
            String cursor,
            UUID idAfter,
            int limit,
            String sortBy,
            String sortDirection,
            String emailLike,
            UserRole roleEqual,
            Boolean locked
    ) {
        Slice<User> slice = userRepository.findUsers(
                cursor,
                idAfter,
                limit,
                sortBy,
                sortDirection,
                emailLike,
                roleEqual,
                locked
        );

        long totalCount = userRepository.countUsers(
                emailLike,
                roleEqual,
                locked
        );

        List<UserDto> data = slice.getContent().stream()
                .map(UserDto::from)
                .toList();

        String nextCursor = null;
        UUID nextIdAfter = null;

        if (slice.hasNext() && !data.isEmpty()) {
            UserDto lastUser = data.get(data.size() - 1);

            nextCursor = switch (sortBy) {
                case "email" -> lastUser.email();
                case "createdAt" -> lastUser.createdAt().toString();
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 정렬 기준입니다: " + sortBy
                );
            };

            nextIdAfter = lastUser.id();
        }

        log.debug("사용자 목록 조회: 조회 건수={}, 전체 건수={}",
                data.size(), totalCount);

        return new UserListResponse(
                data,
                nextCursor,
                nextIdAfter,
                slice.hasNext(),
                totalCount,
                sortBy,
                sortDirection
        );
    }

    @Override
    @Transactional
    public UserDto updateRole(UUID userId, UserRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        user.updateRole(role);

        authSessionService.revokeAllSessions(userId);

        log.info("사용자 권한 변경 완료: userId={}, role={}", userId, role);

        return UserDto.from(user);
    }

    @Override
    @Transactional
    public UserDto updateLock(UUID userId, boolean locked) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (locked) {
            user.lock();
            authSessionService.revokeAllSessions(userId);
        } else {
            user.unlock();
        }

        log.info("사용자 계정 잠금 상태 변경: userId={}, locked={}", userId, locked);

        return UserDto.from(user);
    }

    @Override
    @Transactional
    public void updatePassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        String encodedPassword = passwordEncoder.encode(newPassword);

        user.changePassword(encodedPassword);
        user.clearTemporaryPassword();

        authSessionService.revokeAllSessions(userId);

        log.info("사용자 비밀번호 변경 완료: userId={}", userId);
    }




}