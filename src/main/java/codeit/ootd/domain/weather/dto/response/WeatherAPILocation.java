package codeit.ootd.domain.weather.dto.response;

import java.util.List;

public record WeatherAPILocation(
    double latitude,
    double longitude,
    double x,
    double y,
    List<String> locationNames
) {

}
