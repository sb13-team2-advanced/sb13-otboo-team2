package codeit.ootd.domain.feed.dto.response;

public record TemperatureDto(
        double current,
        double comparedToDayBefore,
        double min,
        double max
) {
}