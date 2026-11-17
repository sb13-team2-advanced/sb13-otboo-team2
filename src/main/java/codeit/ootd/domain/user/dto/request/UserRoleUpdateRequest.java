package codeit.ootd.domain.user.dto.request;

import codeit.ootd.domain.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(
        @NotNull UserRole role
) {
}
