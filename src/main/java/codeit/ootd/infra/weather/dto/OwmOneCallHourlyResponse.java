package codeit.ootd.infra.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OwmOneCallHourlyResponse(
    List<HourlyItem> data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HourlyItem(
        long dt,
        double temp,
        short humidity,
        @JsonProperty("wind_speed") double windSpeed,
        List<WeatherCondition> weather,
        Double pop,
        Precipitation rain,
        Precipitation snow
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeatherCondition(
        int id
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Precipitation(
        @JsonProperty("1h") Double amount
    ){
    }
}
