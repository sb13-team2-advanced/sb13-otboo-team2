package codeit.ootd.domain.weather.dto.response;

import java.util.UUID;

public record WeatherDto(
    UUID id,
    WeatherAPILocation location,
    String skyStatus,
    Temperature temperature,
    Humidity humidity,
    Precipitation precipitation,
    WindSpeed windSpeed
) {

    public record Temperature(
        double current,
        double min,
        double max,
        Double comparedToDayBefore
    ) {
    }

    public record Humidity(
        short current,
        Double comparedToDayBefore
    ) {
    }

    public record Precipitation(
        String type,
        double amount,
        double probability
    ) {
    }

    public record WindSpeed(
        double speed,
        String asWord
    ) {
    }
}
