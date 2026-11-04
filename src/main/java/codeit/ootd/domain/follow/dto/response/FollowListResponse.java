package codeit.ootd.domain.follow.dto.response;

import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public record FollowListResponse(
    List<FollowDto> data,
    String nextCursor,
    UUID nextIdAfter,
    Boolean hasNext,
    Long totalCount,
    String sortBy,
    SortDirection sortDirection
) {
}
