package codeit.ootd.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UserPasswordUpdateRequest(
        @NotBlank String password
) {
}
