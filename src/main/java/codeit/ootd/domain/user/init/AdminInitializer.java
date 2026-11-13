package codeit.ootd.domain.user.init;

import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.util.EmailNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@Profile("!test")
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public AdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${otboo.admin.name:관리자}") String adminName,
            @Value("${otboo.admin.email:}") String adminEmail,
            @Value("${otboo.admin.password:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminName = adminName;
        this.adminEmail = EmailNormalizer.normalize(adminEmail);
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    public void initialize() {
        if (adminEmail.isBlank()
                || adminEmail.chars().anyMatch(Character::isWhitespace)
                || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "초기 관리자 이메일과 비밀번호 설정을 확인해 주세요."
            );
        }

        Optional<User> existingUser =
                userRepository.findByEmailIgnoreCase(adminEmail);

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            if (user.getRole() != UserRole.ADMIN
                    || user.getStatus() != UserStatus.ACTIVE
                    || user.isLocked()) {
                throw new IllegalStateException(
                        "초기 관리자 이메일에 사용 가능한 ADMIN 계정이 존재하지 않습니다."
                );
            }

            log.info("초기 관리자 계정이 이미 존재합니다.");
            return;
        }

        User admin = User.create(
                adminName,
                adminEmail,
                passwordEncoder.encode(adminPassword)
        );

        admin.updateRole(UserRole.ADMIN);

        userRepository.save(admin);

        log.info("초기 관리자 계정을 생성했습니다.");
    }
}
