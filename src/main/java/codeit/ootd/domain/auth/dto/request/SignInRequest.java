package codeit.ootd.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SignInRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        @Pattern(
                regexp = "^\\S+$",
                message = "이메일에는 공백을 포함할 수 없습니다."
        )
        String username,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
