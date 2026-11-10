package codeit.ootd.domain.weather.mapper;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.Weather;
import org.springframework.stereotype.Component;

@Component
public class WeatherDtoMapper {

    public WeatherDto toDto(
        Weather weather,
        Weather previousWeather,
        WeatherAPILocation location
    ) {
        Double temperatureDiff = previousWeather == null
            ? null
            : Math.round(
                (weather.getTemperatureAvg() - previousWeather.getTemperatureAvg()) * 100.0
            ) / 100.0;

        Double humidityDiff = previousWeather == null
            ? null
            : (double) (weather.getHumidityAvg() - previousWeather.getHumidityAvg());

        return new WeatherDto(
            weather.getId(),
            location,
            toSkyStatus(weather.getWeatherCode()),
            new WeatherDto.Temperature(
                weather.getTemperatureAvg(),
                weather.getTemperatureMin(),
                weather.getTemperatureMax(),
                temperatureDiff
            ),
            new WeatherDto.Humidity(
                weather.getHumidityAvg(),
                humidityDiff
            ),
            new WeatherDto.Precipitation(
                toPrecipitationType(weather),
                weather.getRainAmount() + weather.getSnowAmount(),
                weather.getPrecipitationProbability() * 100
            ),
            new WeatherDto.WindSpeed(
                weather.getWindSpeed(),
                toWindStrength(weather.getWindSpeed())
            )
        );
    }

    private String toSkyStatus(int code) {
        if (code == 800 || code == 801) {
            return "CLEAR";
        }
        if (code == 802 || code == 803) {
            return "MOSTLY_CLOUDY";
        }
        return "CLOUDY";
    }

    private String toPrecipitationType(Weather weather) {
        int code = weather.getWeatherCode();

        boolean rain = weather.getRainAmount() > 0
            || (code >= 200 && code < 600);

        boolean snow = weather.getSnowAmount() > 0
            || (code >= 600 && code < 700);

        if (rain && snow) {
            return "RAIN_SNOW";
        }
        if (snow) {
            return "SNOW";
        }
        if (rain && code >=520 && code <=531) {
            return "SHOWER";
        }
        if (rain) {
            return "RAIN";
        }
        return "NONE";
    }

    private String toWindStrength(double speed) {
        if (speed < 5.5) {
            return "WEAK";
        }
        if (speed < 10.8) {
            return "MODERATE";
        }
        return "STRONG";
    }
}
