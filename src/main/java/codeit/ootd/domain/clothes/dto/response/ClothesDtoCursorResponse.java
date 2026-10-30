package codeit.ootd.domain.clothes.dto.response;

import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public record ClothesDtoCursorResponse(
        List<ClothesDto> data,
        String nextCursor,
        UUID nextIdAfter,
        boolean hasNext,
        long totalCount,
        String sortBy,
        SortDirection sortDirection
) {
}
