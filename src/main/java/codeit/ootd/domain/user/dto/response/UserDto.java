package codeit.ootd.domain.user.dto.response;

import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        Instant createdAt,
        String email,
        String name,
        UserRole role,
        boolean locked
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getCreatedAt(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.isLocked()
        );
    }
}
