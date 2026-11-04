package codeit.ootd.domain.user.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserCreateRequestValidationTest {
    
    private static ValidatorFactory factory;
    private static Validator validator;
    
    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    @AfterAll
    static void tearDown() {
        factory.close();
    }
    
    @Test
    @DisplayName("회원가입 시 유효하지 않은 이메일을 입력하면 검증에 실패한다")
    void signup_invalidEmail_failsValidation() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "invalid-email",
                "password123!"
        );

        // when
        var violations = validator.validate(request);

        // then
        assertTrue(
                violations.stream()
                        .anyMatch(v ->
                                v.getPropertyPath().toString()
                                        .equals("email"))
        );
    }

    @Test
    @DisplayName("이름이 공백이면 검증에 실패한다")
    void signUp_blankName_failsValidation() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                " ",
                "test@exaple.com",
                "password123!"
        );

        // when
        var violations = validator.validate(request);

        // then
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getPropertyPath().toString()
                                .equals("name")));
    }

    @Test
    @DisplayName("비밀번호가 공백이면 검증에 실패한다")
    void signup_blankPassword_failsValidation() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                " "
        );

        // when
        var violations = validator.validate(request);

        // then
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getPropertyPath().toString()
                                .equals("password")));
    }
    
    @Test
    @DisplayName("유효한 회원가입 요청은 검증을 통과한다")
    void signup_validRequest_passesValidation() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "password123!"
        );
    
        // when
        var violations = validator.validate(request);
    
        // then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("비밀번호가 UTF-8 기준 72바이트 이하면 검증을 통과한다")
    void password_within72Bytes_isValid() {
        UserCreateRequest englishRequest = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "a".repeat(72)
        );

        UserCreateRequest koreanRequest = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "가".repeat(24)
        );

        assertTrue(validator.validate(englishRequest).isEmpty());
        assertTrue(validator.validate(koreanRequest).isEmpty());
    }

    @Test
    @DisplayName("비밀번호가 UTF-8 기준 72바이트를 초과하면 검증에 실패한다")
    void password_over72Bytes_isInvalid() {
        UserCreateRequest englishRequest = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "a".repeat(73)
        );

        UserCreateRequest koreanRequest = new UserCreateRequest(
                "테스트사용자",
                "test@example.com",
                "가".repeat(25)
        );

        assertFalse(validator.validate(englishRequest).isEmpty());
        assertFalse(validator.validate(koreanRequest).isEmpty());
    }

}
