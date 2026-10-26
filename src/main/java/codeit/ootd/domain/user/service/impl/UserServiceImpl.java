package codeit.ootd.domain.user.service.impl;

import codeit.ootd.domain.user.exception.DuplicateEmailException;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public void signup(String email) {
        log.debug("회원가입 이메일 중복 검사 시작");

        if (userRepository.existsByEmail(email)) {
            log.warn("회원가입 거부: 이메일 중복");
            throw new DuplicateEmailException();
        }

        log.debug("회원가입 이메일 중복 검사 통과");
    }
}
