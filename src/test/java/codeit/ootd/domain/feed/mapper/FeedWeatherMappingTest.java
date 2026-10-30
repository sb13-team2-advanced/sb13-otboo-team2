package codeit.ootd.domain.feed.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.response.WeatherSummaryDto;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("날씨 응답의 피드 매핑 테스트")
class FeedWeatherMappingTest {

    private final FeedMapper feedMapper =
            Mappers.getMapper(FeedMapper.class);

    @Test
    @DisplayName("날씨 ID와 변환된 날씨 값을 피드 요약 응답에 그대로 전달한다")
    void mapsWeatherResponse() {
        UUID weatherId = UUID.randomUUID();

        WeatherDto weather = new WeatherDto(
                weatherId,
                null,
                "MOSTLY_CLOUDY",
                new WeatherDto.Temperature(18.5, 12.0, 22.0, -1.25),
                new WeatherDto.Humidity((short) 65, null),
                new WeatherDto.Precipitation("RAIN", 2.4, 60.0),
                new WeatherDto.WindSpeed(3.0, "WEAK")
        );

        WeatherSummaryDto result =
                feedMapper.toWeatherSummaryDto(weather);

        assertThat(result.weatherId()).isEqualTo(weatherId);
        assertThat(result.skyStatus()).isEqualTo("MOSTLY_CLOUDY");

        assertThat(result.temperature().current()).isEqualTo(18.5);
        assertThat(result.temperature().min()).isEqualTo(12.0);
        assertThat(result.temperature().max()).isEqualTo(22.0);
        assertThat(result.temperature().comparedToDayBefore())
                .isEqualTo(-1.25);

        assertThat(result.precipitation().type()).isEqualTo("RAIN");
        assertThat(result.precipitation().amount()).isEqualTo(2.4);
        assertThat(result.precipitation().probability()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("전날 대비 기온이 없으면 피드 요약 응답에도 null을 유지한다")
    void preservesMissingTemperatureComparison() {
        WeatherDto weather = new WeatherDto(
                UUID.randomUUID(),
                null,
                "CLEAR",
                new WeatherDto.Temperature(18.5, 12.0, 22.0, null),
                new WeatherDto.Humidity((short) 65, null),
                new WeatherDto.Precipitation("NONE", 0.0, 0.0),
                new WeatherDto.WindSpeed(3.0, "WEAK")
        );

        WeatherSummaryDto result =
                feedMapper.toWeatherSummaryDto(weather);

        assertThat(result.temperature().comparedToDayBefore()).isNull();
    }
}