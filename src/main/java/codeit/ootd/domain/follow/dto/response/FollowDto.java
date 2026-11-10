package codeit.ootd.domain.follow.dto.response;

import codeit.ootd.domain.user.dto.response.UserSummary;
import java.util.UUID;

public record FollowDto(
    UUID id,
    UserSummary followee,
    UserSummary follower
) {
}
