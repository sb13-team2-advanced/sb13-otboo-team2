package codeit.ootd.domain.feed.dto.response;

import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public record FeedDtoCursorResponse(
        List<FeedDto> data,
        String nextCursor,
        UUID nextIdAfter,
        boolean hasNext,
        long totalCount,
        String sortBy,
        SortDirection sortDirection
) {
}