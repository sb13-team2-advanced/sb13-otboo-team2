package codeit.ootd.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.auth.service.impl.SecureTemporaryPasswordGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureTemporaryPasswordGeneratorTest {

    private final SecureTemporaryPasswordGenerator generator =
            new SecureTemporaryPasswordGenerator();

    @Test
    @DisplayName("임시 비밀번호는 허용된 문자로 구성된 16자리 문자열이다")
    void generate_validTemporaryPassword() {
        // when
        String password = generator.generate();

        // then
        assertThat(password)
                .hasSize(16)
                .matches("[A-HJ-NP-Za-km-z2-9]+");
    }
}
