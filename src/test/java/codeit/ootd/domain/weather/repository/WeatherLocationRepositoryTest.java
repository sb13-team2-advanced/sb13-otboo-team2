package codeit.ootd.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("수집 예정 시각이 지난 위치를 오래된 순서로 조회한다")
    void findLocationsDueForCollection() {

        Instant now = Instant.parse("2026-10-27T05:00:00Z");

        WeatherLocation past = new WeatherLocation(
            "cell-past",
            37.5665,
            126.9780,
            now.minusSeconds(3600)
        );

        WeatherLocation current = new WeatherLocation(
            "cell-current",
            37.5700,
            126.9800,
            now
        );

        WeatherLocation future = new WeatherLocation(
            "cell-future",
            37.5800,
            126.9900,
            now.plusSeconds(3600)
        );

        repository.saveAllAndFlush(List.of(past, current, future));

        List<WeatherLocation> result =
            repository.findByNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(now);

        assertThat(result)
            .extracting(WeatherLocation::getCellId)
            .containsExactly("cell-past", "cell-current");
    }
}