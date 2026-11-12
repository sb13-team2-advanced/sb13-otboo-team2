package codeit.ootd.domain.feed.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("피드 요청 Bean Validation 테스트")
class FeedRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("등록 내용이 없거나 공백이면 검증에 실패한다")
    void rejectsBlankCreateContent(String content) {
        assertThat(validator.validate(createRequest(content)))
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("content"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("수정 내용이 없거나 공백이면 검증에 실패한다")
    void rejectsBlankUpdateContent(String content) {
        assertThat(validator.validate(new FeedUpdateRequest(content)))
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("content"));
    }

    @ParameterizedTest(name = "내용 {0}자")
    @ValueSource(ints = {1, 1000})
    @DisplayName("등록과 수정 내용은 1000자까지 허용한다")
    void acceptsContentWithinLimit(int length) {
        String content = "가".repeat(length);

        assertThat(validator.validate(createRequest(content))).isEmpty();
        assertThat(validator.validate(new FeedUpdateRequest(content))).isEmpty();
    }

    @ParameterizedTest(name = "내용 {0}자")
    @ValueSource(ints = {1001, 2000})
    @DisplayName("등록과 수정 내용이 1000자를 초과하면 검증에 실패한다")
    void rejectsContentOverLimit(int length) {
        String content = "가".repeat(length);

        assertThat(validator.validate(createRequest(content)))
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("content"));

        assertThat(validator.validate(new FeedUpdateRequest(content)))
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("content"));
    }

    private FeedCreateRequest createRequest(String content) {
        return new FeedCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                content
        );
    }
}