package codeit.ootd.domain.user.dto.request;

import codeit.ootd.global.validation.Utf8ByteSize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100)
        String name,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100)
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Utf8ByteSize(max = 72)
        String password
) {
}
