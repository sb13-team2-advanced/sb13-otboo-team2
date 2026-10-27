package codeit.ootd.domain.directmessage.dto;

// import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public record DirectMessageDtoCursorResponse(
    List<DirectMessageDto> data,
    String nextCursor,
    UUID nextIdAfter,
    Boolean hasNext,
    Long totalCount,
    String sortBy
    // , SortDirection sortDirection
) {
}
