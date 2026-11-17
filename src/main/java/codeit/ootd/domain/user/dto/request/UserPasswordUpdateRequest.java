package codeit.ootd.domain.user.dto.request;

import codeit.ootd.global.validation.Utf8ByteSize;
import jakarta.validation.constraints.NotBlank;

public record UserPasswordUpdateRequest(
        @NotBlank
        @Utf8ByteSize(max = 72)
        String password
) {
}
