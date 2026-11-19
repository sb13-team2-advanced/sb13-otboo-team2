package codeit.ootd.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record PrecipitationDto(
        @Schema(
                description = "강수 타입",
                allowableValues = {"NONE", "RAIN", "RAIN_SNOW", "SNOW", "SHOWER"}
        )
        String type,

        @Schema(description = "강수량")
        double amount,

        @Schema(description = "강수 확률")
        double probability
) {
}