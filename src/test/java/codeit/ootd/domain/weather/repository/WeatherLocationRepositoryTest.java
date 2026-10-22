package codeit.ootd.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WeatherLocationRepositoryTest {

    @Autowired
    private WeatherLocationRepository repository;

    @Test
    @DisplayName("셀 ID로 날씨 수집 위치를 조회한다.")
    void findWeatherLocationByCellId() {

        String cellId = "8828308281fffff";

        WeatherLocation location = new WeatherLocation(
            cellId,
            37.5665,
            126.978,
            Instant.now().plusSeconds(3600)
        );

        repository.saveAndFlush(location);

        WeatherLocation found = repository.findByCellId(cellId).orElseThrow();

        assertThat(found.getCellId()).isEqualTo(cellId);
    }

    @Test
    @DisplayName("동일한 H3 셀 ID는 중복 등록할 수 없다")
    void rejectDuplicateWeatherLocationByCellId() {

        String cellId = "8828308281fffff";

        WeatherLocation first = new WeatherLocation(
            cellId,
            37.5665,
            126.9780,
            Instant.parse("2026-10-22T06:00:00Z")
        );

        WeatherLocation duplicate = new WeatherLocation(
            cellId,
            37.5665,
            126.9780,
            Instant.parse("2026-10-22T07:00:00Z")
        );

        repository.saveAndFlush(first);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}