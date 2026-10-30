package codeit.ootd.domain.follow.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record FollowCreateRequest(@NotNull UUID followeeId, @NotNull UUID followerId) {
}
