package codeit.ootd.domain.notification.dto;

// import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public record NotificationDtoCursorResponse(
    List<NotificationDto> data,
    String nextCursor,
    UUID nextIdAfter,
    Boolean hasNext,
    Long totalCount,
    String sortBy
    // , SortDirection sortDirection
) {
}
