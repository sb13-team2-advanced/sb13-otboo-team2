package codeit.ootd.domain.feed.dto.response;

import codeit.ootd.global.enums.SortDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record FeedDtoCursorResponse(
        List<FeedDto> data,

        @Schema(description = "다음 커서")
        String nextCursor,

        @Schema(description = "다음 요청의 보조 커서")
        UUID nextIdAfter,

        @Schema(description = "다음 데이터가 있는지 여부")
        boolean hasNext,

        @Schema(description = "총 데이터 개수")
        long totalCount,

        @Schema(description = "정렬 기준")
        String sortBy,

        @Schema(description = "정렬 방향")
        SortDirection sortDirection
) {
}