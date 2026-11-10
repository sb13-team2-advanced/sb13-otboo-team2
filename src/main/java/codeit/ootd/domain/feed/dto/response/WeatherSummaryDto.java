package codeit.ootd.domain.feed.dto.response;

import java.util.UUID;

public record WeatherSummaryDto(
        UUID weatherId,
        String skyStatus,
        PrecipitationDto precipitation,
        TemperatureDto temperature
) {
}