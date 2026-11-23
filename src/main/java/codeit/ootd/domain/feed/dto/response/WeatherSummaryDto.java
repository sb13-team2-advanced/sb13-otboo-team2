package codeit.ootd.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "날씨 요약 정보")
public record WeatherSummaryDto(
        @Schema(description = "날씨 ID")
        UUID weatherId,

        @Schema(
                description = "날씨 상태",
                allowableValues = {"CLEAR", "MOSTLY_CLOUDY", "CLOUDY"}
        )
        String skyStatus,

        @Schema(description = "강수 정보")
        PrecipitationDto precipitation,

        @Schema(description = "온도")
        TemperatureDto temperature
) {
}