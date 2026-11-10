package codeit.ootd.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WeatherForecastRepositoryTest {

    @Autowired
    private WeatherLocationRepository locationRepository;

    @Autowired
    private WeatherForecastRepository forecastRepository;

    @Test
    @DisplayName("위치와 예보 시각으로 날씨 예보를 조회한다.")
    void findWeatherForecastByLocationAndForecastAt() {

        WeatherLocation location = new WeatherLocation(
            "8828308281fffff",
            37.5665,
            126.9780,
            Instant.parse("2026-10-22T06:00:00Z")
        );
        locationRepository.saveAndFlush(location);

        Instant forecastAt = Instant.parse("2026-10-22T06:00:00Z");

        WeatherForecast forecast = new WeatherForecast(
            location.getId(),
            forecastAt,
            Instant.parse("2026-10-22T06:00:00Z"),
            18.5,
            (short) 65,
            800,
            0.2,
            0.0,
            0.0,
            2.5
            );

        forecastRepository.saveAndFlush(forecast);

        WeatherForecast found = forecastRepository
            .findByLocationIdAndForecastAt(location.getId(), forecastAt)
            .orElseThrow();

        assertThat(found.getId()).isEqualTo(forecast.getId());
        assertThat(found.getTemperature()).isEqualTo(18.5);
        assertThat(found.getForecastAt()).isEqualTo(forecastAt);
    }

    @Test
    @DisplayName("같은 위치와 예보 시각의 날씨 예보는 중복 저장할 수 없다.")
    void rejectDuplicateForecastByLocationAndForecastAt() {

        WeatherLocation location = new WeatherLocation(
            "8828308281fffff",
             37.5665,
             126.9780,
            Instant.parse("2026-10-22T06:00:00Z")
        );

        locationRepository.saveAndFlush(location);

        Instant forecastAt = Instant.parse("2022-10-22T09:00:00Z");
        Instant collectedAt = Instant.parse("2022-10-22T06:00:00Z");

        WeatherForecast first = new WeatherForecast(
            location.getId(), forecastAt, collectedAt,
            18.5, (short) 65, 800, 0.2,
            0.0, 0.0, 2.5
        );

        WeatherForecast duplicate = new WeatherForecast(
            location.getId(), forecastAt, collectedAt,
            20.0, (short) 70, 801, 0.5,
            0.0, 0.0, 3.0
        );

        forecastRepository.saveAndFlush(first);

        assertThatThrownBy(() -> forecastRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("위치와 날짜 범위에 맞는 예보만 시간순으로 조회한다")
    void findForecastsForPeriodFiltersByLocationAndTime() {

        WeatherLocation location = saveLocation("8828308281fffff");
        WeatherLocation otherLocation = saveLocation("8828308283fffff");

        Instant start = Instant.parse("2026-10-25T15:00:00Z");
        Instant end = Instant.parse("2026-10-26T15:00:00Z");

        WeatherForecast later = saveForecast(
            location, "2026-10-26T12:00:00Z"
        );

        WeatherForecast earlier = saveForecast(
            location, "2026-10-26T03:00:00Z"
        );

        saveForecast(location, "2026-10-27T03:00:00Z");

        saveForecast(otherLocation, "2026-10-26T03:00:00Z");

        List<WeatherForecast> result =
            forecastRepository.findForecastsForPeriod(
                location.getId(), start, end
            );

        assertThat(result)
            .extracting(WeatherForecast::getId)
            .containsExactly(earlier.getId(), later.getId());
    }

    @Test
    @DisplayName("KST 날짜 시작은 포함하고 다음 날 시작은 제외한다")
    void findForecastsForPeriodUsesExclusiveEnd() {

        WeatherLocation location = saveLocation("8828308281fffff");

        Instant start = Instant.parse("2026-10-25T15:00:00Z");

        Instant end = Instant.parse("2026-10-26T15:00:00Z");

        saveForecast(location, "2026-10-25T14:59:59Z");

        WeatherForecast atStart = saveForecast(
            location, "2026-10-25T15:00:00Z"
        );

        WeatherForecast beforeEnd = saveForecast(
            location, "2026-10-26T14:59:59Z"
        );

        saveForecast(location, "2026-10-26T15:00:00Z");

        List<WeatherForecast> result =
            forecastRepository.findForecastsForPeriod(
                location.getId(), start, end
            );

        assertThat(result)
            .extracting(WeatherForecast::getId)
            .containsExactly(atStart.getId(), beforeEnd.getId());
    }

    @Test
    @DisplayName("해당 기간에 예보가 없으면 빈 목록을 반환한다")
    void findForecastsForPeriodReturnsEmpty() {

        WeatherLocation location = saveLocation("8828308281fffff");

        saveForecast(location, "2026-10-27T03:00:00Z");

        Instant start = Instant.parse("2026-10-25T15:00:00Z");
        Instant end = Instant.parse("2026-10-26T15:00:00Z");

        List<WeatherForecast> result =
            forecastRepository.findForecastsForPeriod(
                location.getId(), start, end
            );

        assertThat(result).isEmpty();
    }

    private WeatherLocation saveLocation(String cellId) {

        WeatherLocation location = new WeatherLocation(
            cellId,
            37.5665,
            126.9780,
            Instant.parse("2026-10-27T00:00:00Z")
        );

        return locationRepository.saveAndFlush(location);
    }

    private WeatherForecast saveForecast(
        WeatherLocation location,
        String forecastAt
    ) {
        WeatherForecast forecast = new WeatherForecast(
            location.getId(),
            Instant.parse(forecastAt),
            Instant.parse("2026-10-25T12:00:00Z"),
            18.5,
            (short) 65,
            800,
            0.2,
            0.0,
            0.0,
            2.5
        );

        return forecastRepository.saveAndFlush(forecast);
    }
}