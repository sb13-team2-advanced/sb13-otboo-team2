package codeit.ootd.domain.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherLocationTest {

    @Test
    @DisplayName("수집 완료 시 마지막 수집 시각과 다음 수집 예정 시각이 갱신된다.")
    void completeCollectionUpdatesCollectionTimes() {

        Instant initialReFreshAt = Instant.parse("2026-10-23T00:00:00Z");

        WeatherLocation location = new WeatherLocation(
            "8828308281fffff",
              37.5665,
              126.9780,
            initialReFreshAt
        );

        Instant collectedAt = Instant.parse("2026-10-23T00:10:00Z");
        Instant nextRefreshAt = Instant.parse("2026-10-23T01:10:00Z");

        location.completeCollection(collectedAt, nextRefreshAt);

        assertThat(location.getLastCollectedAt()).isEqualTo(collectedAt);
        assertThat(location.getNextRefreshAt()).isEqualTo(nextRefreshAt);
    }

    @Test
    @DisplayName("다음 수집 예정 시각이 과거이면 예외가 발생한다")
    void completeCollectionRejectsInvalidNextRefreshAt() {

        Instant initialRefreshAt = Instant.parse("2026-10-23T00:00:00Z");

        WeatherLocation location = new WeatherLocation(
            "8828308281fffff",
            37.5665,
            126.9780,
            initialRefreshAt
        );

        Instant collectedAt = Instant.parse("2026-10-23T01:00:00Z");
        Instant invalidNextRefreshAt = Instant.parse("2026-10-23T00:30:00Z");

        assertThatThrownBy(() ->
            location.completeCollection(collectedAt, invalidNextRefreshAt)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(location.getLastCollectedAt()).isNull();
        assertThat(location.getNextRefreshAt()).isEqualTo(initialRefreshAt);
    }

    @Test
    @DisplayName("다음 수집 예정 시각이 수집 시각과 같으면 예외가 발생한다")
    void completeCollectionRejectsEqualRefreshAt() {

        Instant initialRefreshAt = Instant.parse("2026-10-23T00:00:00Z");

        WeatherLocation location = new WeatherLocation(
            "8828308281fffff",
            37.5665,
            126.9780,
            initialRefreshAt
        );

        Instant collectedAt = Instant.parse("2026-10-23T01:00:00Z");

        assertThatThrownBy(() ->
            location.completeCollection(collectedAt, collectedAt)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(location.getLastCollectedAt()).isNull();
        assertThat(location.getNextRefreshAt()).isEqualTo(initialRefreshAt);
    }
}