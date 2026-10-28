package codeit.ootd.infra.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OwmForecastResponse(
    List<ForecastItem> list
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastItem(
        long dt,
        Main main,
        List<WeatherCondition> weather,
        double pop,
        Precipitation rain,
        Precipitation snow,
        Wind wind
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
        double temp,
        short humidity
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeatherCondition(
        int id
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Precipitation(
        @JsonProperty("3h") double amount
    ){
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(
        double speed
    ) {
    }
}
