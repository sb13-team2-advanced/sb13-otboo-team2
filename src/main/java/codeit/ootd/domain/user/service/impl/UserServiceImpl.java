package codeit.ootd.domain.user.service.impl;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}