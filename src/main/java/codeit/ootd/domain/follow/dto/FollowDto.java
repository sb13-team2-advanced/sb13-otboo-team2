package codeit.ootd.domain.follow.dto;

import java.util.UUID;

public record FollowDto(
    UUID id
    // User dto 추가되면 복원
    // , UserSummary followee
    // , UserSummary follower
) {
}
