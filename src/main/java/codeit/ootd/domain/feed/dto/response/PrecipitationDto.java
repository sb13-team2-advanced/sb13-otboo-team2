package codeit.ootd.domain.feed.dto.response;

public record PrecipitationDto(
        String type,
        double amount,
        double probability
) {
}