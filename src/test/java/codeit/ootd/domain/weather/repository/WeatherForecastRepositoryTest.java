package codeit.ootd.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import java.time.Instant;
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
            location,
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
            .findByLocationAndForecastAt(location, forecastAt)
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
            location, forecastAt, collectedAt,
            18.5, (short) 65, 800, 0.2,
            0.0, 0.0, 2.5
        );

        WeatherForecast duplicate = new WeatherForecast(
            location, forecastAt, collectedAt,
            20.0, (short) 70, 801, 0.5,
            0.0, 0.0, 3.0
        );

        forecastRepository.saveAndFlush(first);

        assertThatThrownBy(() -> forecastRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}