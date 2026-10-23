package codeit.ootd.domain.follow.dto.request;

import java.util.UUID;

public record FollowCreateRequest(UUID followeeId, UUID followerId) {
}
