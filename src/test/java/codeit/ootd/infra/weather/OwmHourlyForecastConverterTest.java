package codeit.ootd.infra.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse.HourlyItem;
import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse.Precipitation;
import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse.WeatherCondition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OwmHourlyForecastConverterTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final OwmHourlyForecastConverter converter =
        new OwmHourlyForecastConverter();

    @Test
    @DisplayName("1시간 강수량과 적설량을 합산해 3시간 예보로 변환한다")
    void convertAggregatesThreeHourPrecipitation() {
        List<HourlyItem> items = List.of(
            item(1, 500, 0.3, 0.52, 0.1),
            item(2, 500, 0.8, 0.15, 0.2),
            item(3, 500, 0.6, 0.23, 0.3)
        );

        List<OwmHourlyForecastConverter.ThreeHourForecast> result =
            converter.convert(items);

        assertThat(result).hasSize(1);

        OwmHourlyForecastConverter.ThreeHourForecast forecast = result.get(0);

        assertThat(forecast.forecastAt())
            .isEqualTo(Instant.ofEpochSecond(timestamp(3)));
        assertThat(forecast.temperature()).isEqualTo(13.0);
        assertThat(forecast.humidity()).isEqualTo((short) 73);
        assertThat(forecast.weatherCode()).isEqualTo(500);
        assertThat(forecast.windSpeed()).isEqualTo(0.3);
        assertThat(forecast.precipitationProbability()).isEqualTo(0.8);
        assertThat(forecast.rainAmount()).isCloseTo(0.9, offset(0.000001));
        assertThat(forecast.snowAmount()).isCloseTo(0.6, offset(0.000001));
    }

    @Test
    @DisplayName("자정에는 전날 시간별 자료가 없어도 00시 예보를 생성한다")
    void convertMidnightWithoutPreviousHours() {
        List<HourlyItem> items = List.of(
            item(0, 803, 0.7, 0.2, 0.3)
        );

        List<OwmHourlyForecastConverter.ThreeHourForecast> result =
            converter.convert(items);

        assertThat(result).hasSize(1);

        OwmHourlyForecastConverter.ThreeHourForecast forecast = result.get(0);

        assertThat(forecast.forecastAt())
            .isEqualTo(Instant.ofEpochSecond(timestamp(0)));
        assertThat(forecast.weatherCode()).isEqualTo(803);
        assertThat(forecast.rainAmount()).isZero();
        assertThat(forecast.snowAmount()).isZero();
        assertThat(forecast.precipitationProbability()).isNull();
    }

    @Test
    @DisplayName("필요한 1시간 자료가 누락되면 해당 3시간 예보를 생성하지 않는다")
    void convertSkipsIncompleteThreeHourSlot() {
        List<HourlyItem> items = List.of(
            item(1, 803, 0.1, null, null),
            item(3, 500, 0.8, 0.5, null)
        );

        List<OwmHourlyForecastConverter.ThreeHourForecast> result =
            converter.convert(items);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("강수량 필드가 없으면 0으로 처리하고 강수확률 누락은 유지한다")
    void convertHandlesMissingPrecipitationFields() {
        List<HourlyItem> items = List.of(
            item(1, 803, null, null, null),
            item(2, 803, 0.4, null, null),
            item(3, 803, 0.6, 0.2, null)
        );

        List<OwmHourlyForecastConverter.ThreeHourForecast> result =
            converter.convert(items);

        assertThat(result).hasSize(1);

        OwmHourlyForecastConverter.ThreeHourForecast forecast = result.get(0);

        assertThat(forecast.rainAmount()).isEqualTo(0.2);
        assertThat(forecast.snowAmount()).isZero();
        assertThat(forecast.precipitationProbability()).isNull();
    }

    @Test
    @DisplayName("시간별 데이터의 입력 순서와 관계없이 예보를 시간순으로 반환한다")
    void convertSortsForecastsByTime() {
        List<HourlyItem> items = List.of(
            item(6, 803, 0.2, null, null),
            item(3, 802, 0.3, null, null),
            item(0, 800, null, null, null),
            item(5, 803, 0.2, null, null),
            item(1, 802, 0.3, null, null),
            item(4, 803, 0.2, null, null),
            item(2, 802, 0.3, null, null)
        );

        List<OwmHourlyForecastConverter.ThreeHourForecast> result =
            converter.convert(items);

        assertThat(result).hasSize(3);
        assertThat(result)
            .extracting(OwmHourlyForecastConverter.ThreeHourForecast::forecastAt)
            .containsExactly(
                Instant.ofEpochSecond(timestamp(0)),
                Instant.ofEpochSecond(timestamp(3)),
                Instant.ofEpochSecond(timestamp(6))
            );
    }

    @Test
    @DisplayName("동일한 시각의 날씨 데이터가 중복되면 예외가 발생한다")
    void convertRejectsDuplicateTimestamps() {
        List<HourlyItem> items = List.of(
            item(3, 803, 0.2, null, null),
            item(3, 500, 0.8, 0.5, null)
        );

        assertThatThrownBy(() -> converter.convert(items))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OWM 시간별 날씨 데이터에 중복된 시각이 포함되어 있습니다.");
    }

    private HourlyItem item(
        int hour,
        int weatherCode,
        Double pop,
        Double rain,
        Double snow
    ) {
        return new HourlyItem(
            timestamp(hour),
            10.0 + hour,
            (short) (70 + hour),
            hour / 10.0,
            List.of(new WeatherCondition(weatherCode)),
            pop,
            rain == null ? null : new Precipitation(rain),
            snow == null ? null : new Precipitation(snow)
        );
    }

    private long timestamp(int hour) {
        return LocalDate.of(2026, 11, 14)
            .atTime(hour, 0)
            .atZone(KOREA_ZONE)
            .toEpochSecond();
    }
}
