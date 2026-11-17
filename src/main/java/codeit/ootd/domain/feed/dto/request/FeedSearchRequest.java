package codeit.ootd.domain.feed.dto.request;

import codeit.ootd.global.enums.SortDirection;
import java.util.UUID;

public record FeedSearchRequest(
        String cursor,
        UUID idAfter,
        Integer limit,
        String sortBy,
        SortDirection sortDirection,
        String keywordLike,
        String skyStatusEqual,
        String precipitationTypeEqual,
        UUID authorIdEqual
) {
}