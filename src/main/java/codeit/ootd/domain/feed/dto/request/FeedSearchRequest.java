package codeit.ootd.domain.feed.dto.request;

import codeit.ootd.global.enums.SortDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record FeedSearchRequest(
        String cursor,
        UUID idAfter,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer limit,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"createdAt", "likeCount"}
        )
        String sortBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SortDirection sortDirection,

        String keywordLike,

        @Schema(allowableValues = {"CLEAR", "MOSTLY_CLOUDY", "CLOUDY"})
        String skyStatusEqual,

        @Schema(allowableValues = {"NONE", "RAIN", "RAIN_SNOW", "SNOW", "SHOWER"})
        String precipitationTypeEqual,

        UUID authorIdEqual
) {
}