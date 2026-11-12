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

    @Test
    @DisplayName("임시 비밀번호는 만료 시각 이전에만 유효하다")
    void temporaryPasswordIsValidBeforeExpiration() {
        // given
        User user = User.create(
                "테스트",
                "test@example.com",
                "original-password"
        );

        Instant issuedAt = Instant.parse("2026-11-10T06:00:00Z");
        Instant expiresAt = issuedAt.plusSeconds(180);

        user.issueTemporaryPassword("encoded-temporary-password", expiresAt);

        // when & then
        assertThat(user.hasValidTemporaryPassword(issuedAt.plusSeconds(179)))
                .isTrue();

        assertThat(user.hasValidTemporaryPassword(expiresAt))
                .isFalse();
    }

    @Test
    @DisplayName("발급되지 않았거나 파기된 임시 비밀번호는 유효하지 않다")
    void temporaryPasswordIsInvalidWhenAbsent() {
        // given
        User user = User.create(
                "테스트",
                "test@example.com",
                "original-password"
        );

        Instant now = Instant.parse("2026-11-10T06:00:00Z");

        // when & then
        assertThat(user.hasValidTemporaryPassword(now)).isFalse();

        user.issueTemporaryPassword(
                "encoded-temporary-password",
                now.plusSeconds(180)
        );
        user.clearTemporaryPassword();

        assertThat(user.hasValidTemporaryPassword(now)).isFalse();
    }

}
