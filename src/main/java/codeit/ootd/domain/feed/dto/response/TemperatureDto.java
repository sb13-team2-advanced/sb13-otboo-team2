package codeit.ootd.domain.feed.dto.response;

public record TemperatureDto(
        double current,
        Double comparedToDayBefore,
        double min,
        double max
) {
}