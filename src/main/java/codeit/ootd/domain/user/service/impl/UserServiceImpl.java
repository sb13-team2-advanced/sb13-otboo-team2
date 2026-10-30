package codeit.ootd.domain.user.service.impl;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.dto.response.UserListResponse;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
        Sort.Direction direction = switch (sortDirection) {
            case "ASCENDING" -> Sort.Direction.ASC;
            case "DESCENDING" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 정렬 방향입니다: " + sortDirection
            );
        };

        Pageable pageable = PageRequest.of(
                0,
                limit,
                Sort.by(direction, sortBy)
        );

        Page<User> page = userRepository.findAll(pageable);

        List<UserDto> data = page.getContent().stream()
                .map(UserDto::from)
                .toList();

        log.debug("사용자 목록 첫 페이지 조회: 조회 건수={}", data.size());

        String nextCursor = null;
        UUID nextIdAfter = null;

        if (page.hasNext() && !data.isEmpty()) {
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

        return new UserListResponse(
                data,
                nextCursor,
                nextIdAfter,
                page.hasNext(),
                page.getTotalElements(),
                sortBy,
                sortDirection
        );
    }
}