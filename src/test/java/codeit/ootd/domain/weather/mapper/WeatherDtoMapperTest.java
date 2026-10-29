package codeit.ootd.domain.weather.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.Weather;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherDtoMapperTest {

    private final WeatherDtoMapper mapper = new WeatherDtoMapper();

    @Test
    @DisplayName("toDto는 일별 날씨를 프론트 응답 형식으로 변환한다")
    void toDtoConvertsWeather() {
        UUID locationId = UUID.randomUUID();

        Weather today = createWeather(
            locationId, LocalDate.of(2026, 10, 28),
            19.5, (short) 70, 500, 0.7, 2.5, 7.2
        );

        Weather yesterday = createWeather(
            locationId, LocalDate.of(2026, 10, 27),
            16.0, (short) 60, 800, 0.0, 0.0, 2.0
        );

        WeatherAPILocation location = createLocation();

        WeatherDto result = mapper.toDto(today, yesterday, location);

        assertThat(result.location()).isEqualTo(location);
        assertThat(result.skyStatus()).isEqualTo("CLOUDY");

        assertThat(result.temperature().current()).isEqualTo(19.5);
        assertThat(result.temperature().min()).isEqualTo(14.0);
        assertThat(result.temperature().max()).isEqualTo(24.0);
        assertThat(result.temperature().comparedToDayBefore())
            .isEqualTo(3.5);

        assertThat(result.humidity().current()).isEqualTo((short) 70);
        assertThat(result.humidity().comparedToDayBefore())
            .isEqualTo(10.0);

        assertThat(result.precipitation().type()).isEqualTo("RAIN");
        assertThat(result.precipitation().amount()).isEqualTo(2.5);
        assertThat(result.precipitation().probability()).isEqualTo(70.0);

        assertThat(result.windSpeed().speed()).isEqualTo(7.2);
        assertThat(result.windSpeed().asWord()).isEqualTo("MODERATE");
    }

    @Test
    @DisplayName("toDto는 전날 데이터가 없으면 비교값을 null로 반환한다")
    void toDtoReturnsNullWithoutPreviousWeather() {
        Weather today = createWeather(
            UUID.randomUUID(), LocalDate.of(2026, 10, 28),
            17.0, (short) 65, 800, 0.0, 0.0, 2.5
        );

        WeatherDto result = mapper.toDto(today, null, createLocation());

        assertThat(result.temperature().comparedToDayBefore()).isNull();
        assertThat(result.humidity().comparedToDayBefore()).isNull();
        assertThat(result.skyStatus()).isEqualTo("CLEAR");
        assertThat(result.precipitation().type()).isEqualTo("NONE");
        assertThat(result.windSpeed().asWord()).isEqualTo("WEAK");
    }

    private Weather createWeather(
        UUID locationId,
        LocalDate date,
        double temperature,
        short humidity,
        int weatherCode,
        double probability,
        double rainAmount,
        double windSpeed
    ) {
        return new Weather(
            locationId,
            date,
            Instant.parse("2026-10-28T06:00:00Z"),
            temperature,
            14.0,
            24.0,
            humidity,
            weatherCode,
            probability,
            rainAmount,
            0.0,
            windSpeed,
            (short) 8
        );
    }

    private WeatherAPILocation createLocation() {
        return new WeatherAPILocation(
            37.5665,
            126.9780,
            37.5665,
            126.9780,
            List.of("서울특별시", "중구", "명동")
        );
    }
}
