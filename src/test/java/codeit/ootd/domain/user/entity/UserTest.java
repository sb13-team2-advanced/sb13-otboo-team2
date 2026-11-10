package codeit.ootd.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    @DisplayName("임시 비밀번호를 발급하면 해시값과 만료 시각이 저장된다")
    void issueTemporaryPassword() {
        // given
        User user = User.create(
                "테스트 사용자",
                "test@example.com",
                "encoded-original-password"
        );

        String encodedTemporaryPassword = "encoded-temporary-password";
        Instant expiresAt = Instant.parse("2026-11-10T06:00:00Z");

        // when
        user.issueTemporaryPassword(encodedTemporaryPassword, expiresAt);

        // then
        assertThat(user.getTemporaryPassword())
                .isEqualTo(encodedTemporaryPassword);
        assertThat(user.getTemporaryPasswordExpiresAt())
                .isEqualTo(expiresAt);

        // 기존 비밀번호는 변경되지 않아야 한다.
        assertThat(user.getPassword())
                .isEqualTo("encoded-original-password");
    }

    @Test
    @DisplayName("임시 비밀번호를 재발급하면 기존 정보가 교체된다")
    void reissueTemporaryPassword() {
        // given
        User user = User.create("테스트", "test@example.com", "original-password");

        Instant firstExpiresAt = Instant.parse("2026-11-10T06:00:00Z");
        Instant secondExpiresAt = Instant.parse("2026-11-10T06:03:00Z");

        user.issueTemporaryPassword("first-hash", firstExpiresAt);

        // when
        user.issueTemporaryPassword("second-hash", secondExpiresAt);

        // then
        assertThat(user.getTemporaryPassword()).isEqualTo("second-hash");
        assertThat(user.getTemporaryPasswordExpiresAt()).isEqualTo(secondExpiresAt);
    }

    @Test
    @DisplayName("임시 비밀번호를 파기하면 저장된 정보가 삭제된다")
    void clearTemporaryPassword() {
        // given
        User user = User.create("테스트", "test@example.com", "original-password");

        user.issueTemporaryPassword(
                "temporary-hash",
                Instant.parse("2026-11-10T06:03:00Z")
        );

        // when
        user.clearTemporaryPassword();

        // then
        assertThat(user.getTemporaryPassword()).isNull();
        assertThat(user.getTemporaryPasswordExpiresAt()).isNull();
    }
}
