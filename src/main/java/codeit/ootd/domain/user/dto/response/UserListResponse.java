package codeit.ootd.domain.user.dto.response;

import java.util.List;
import java.util.UUID;

public record UserListResponse(
        List<UserDto> data,
        String nextCursor,
        UUID nextIdAfter,
        boolean hasNext,
        long totalCount,
        String sortBy,
        String sortDirection
) {
}