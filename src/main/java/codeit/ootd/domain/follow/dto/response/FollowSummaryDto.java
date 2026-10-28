package codeit.ootd.domain.follow.dto.response;

import java.util.UUID;

public record FollowSummaryDto(
    UUID followeeId,
    Long followerCount,
    Long followingCount,
    Boolean followedByMe,
    UUID followedByMeId,
    Boolean followingMe
) {
}
