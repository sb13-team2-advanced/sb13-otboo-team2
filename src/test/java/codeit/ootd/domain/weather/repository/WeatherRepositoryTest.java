package codeit.ootd.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
            location.getId(),
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
            .findByLocationIdAndWeatherDate(location.getId(), weatherDate)
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
            location.getId(), weatherDate, calculatedAt,
            17.0, 12.0, 21.0,
            (short) 65, 800, 0.3,
            0.0, 0.0, 2.5, (short) 8
        );

        Weather duplicate = new Weather(
            location.getId(), weatherDate, calculatedAt,
            19.0, 13.0, 23.0,
            (short) 70, 801, 0.5,
            0.0, 0.0, 3.0, (short) 8
        );

        weatherRepository.saveAndFlush(first);

        assertThatThrownBy(() -> weatherRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findWeathersByDateRange는 지정한 위치의 날짜 범위를 오름차순으로 조회한다")
    void findWeathersByDateRange() {

        Instant now = Instant.parse("2026-10-28T06:00:00Z");

        WeatherLocation location = new WeatherLocation(
            "cell-range-1", 37.5665, 126.9780, now
        );

        WeatherLocation otherLocation = new WeatherLocation(
            "cell-range-2", 37.5700, 126.9800, now
        );

        locationRepository.saveAllAndFlush(
            List.of(location, otherLocation)
        );

        LocalDate startDate = LocalDate.of(2026, 10, 28);
        LocalDate endDate = LocalDate.of(2026, 11, 1);

        List<Weather> weathers = new java.util.ArrayList<>();

        for (LocalDate date : List.of(
            LocalDate.of(2026, 10, 27),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 10, 28),
            LocalDate.of(2026, 11, 2)
        )) {
            weathers.add(createWeather(location.getId(), date));
        }

        weathers.add(
            createWeather(otherLocation.getId(), startDate)
        );

        weatherRepository.saveAllAndFlush(weathers);

        List<Weather> result = weatherRepository
            .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                location.getId(),
                startDate,
                endDate
            );

        assertThat(result)
            .extracting(Weather::getWeatherDate)
            .containsExactly(
                LocalDate.of(2026, 10, 28),
                LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 11, 1)
            );
    }

    private Weather createWeather(UUID locationId, LocalDate date) {
        return new Weather(
            locationId,
            date,
            Instant.parse("2026-10-28T06:00:00Z"),
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
    }
}