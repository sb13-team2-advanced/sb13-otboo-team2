package codeit.ootd.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank
        @Email
        String email
) {
}
