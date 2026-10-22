package codeit.ootd.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WeatherRepositoryTest {

    @Autowired
    private WeatherLocationRepository locationRepository;

    @Autowired
    private WeatherRepository weatherRepository;

    @Test
    @DisplayName("위치와 날짜로 일별 날씨를 조회한다.")
    void findWeatherByLocationAndWeatherDate() {

        WeatherLocation location = new WeatherLocation(
            "8828308281fffff",
            37.5665,
            126.9780,
            Instant.parse("2026-10-22T06:00:00Z")
        );

        locationRepository.saveAndFlush(location);

        LocalDate weatherDate = LocalDate.of(2026, 10, 23);

        Weather weather = new Weather(
            location,
            weatherDate,
            Instant.parse("2026-10-22T06:00:00Z"),
            17.0,
            12.0,
            21.0,
            (short) 65,
            800,
            0.3,
            0.0,
            0.0,
            2.5,
            (short) 8
        );

        weatherRepository.saveAndFlush(weather);

        Weather found = weatherRepository
            .findByLocationAndWeatherDate(location, weatherDate)
            .orElseThrow();

        assertThat(found.getId()).isEqualTo(weather.getId());
        assertThat(found.getWeatherDate()).isEqualTo(weatherDate);
        assertThat(found.getTemperatureAvg()).isEqualTo(17.0);
        assertThat(found.getForecastSlotCount()).isEqualTo((short) 8);
    }

    @Test
    @DisplayName("같은 위치와 날짜의 일별 날씨는 중복 저장할 수 없다.")
    void rejectDuplicateWeatherByLocationAndDate() {

        WeatherLocation location = new WeatherLocation(
            "8828308281fffff",
             37.5665,
             126.9780,
            Instant.parse("2026-10-22T06:00:00Z")
        );

        locationRepository.saveAndFlush(location);

        LocalDate weatherDate = LocalDate.of(2026, 10, 23);
        Instant calculatedAt = Instant.parse("2026-10-22T06:00:00Z");

        Weather first = new Weather(
            location, weatherDate, calculatedAt,
            17.0, 12.0, 21.0,
            (short) 65, 800, 0.3,
            0.0, 0.0, 2.5, (short) 8
        );

        Weather duplicate = new Weather(
            location, weatherDate, calculatedAt,
            19.0, 13.0, 23.0,
            (short) 70, 801, 0.5,
            0.0, 0.0, 3.0, (short) 8
        );

        weatherRepository.saveAndFlush(first);

        assertThatThrownBy(() -> weatherRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}