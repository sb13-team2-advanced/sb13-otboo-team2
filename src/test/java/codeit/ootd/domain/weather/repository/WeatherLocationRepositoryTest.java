package codeit.ootd.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WeatherLocationRepositoryTest {

    @Autowired
    private WeatherLocationRepository repository;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    @DisplayName("수집 실패 시 10분 뒤로 재시도를 예약하고 그 전에는 조회하지 않는다")
    void scheduleRetryAfterCollectionFailure() {

        Instant collectedAt = Instant.parse("2026-10-28T06:00:00Z");
        Instant failedAt = Instant.parse("2026-10-28T09:00:00Z");
        Instant retryAt = failedAt.plusSeconds(600);

        WeatherLocation location = new WeatherLocation(
            "cell-retry",
            37.5665,
            126.9780,
            collectedAt
        );

        location.completeCollection(
            collectedAt,
            collectedAt.plusSeconds(7200)
        );

        location.scheduleRetry(failedAt, retryAt);
        repository.saveAndFlush(location);

        assertThat(location.getLastCollectedAt()).isEqualTo(collectedAt);
        assertThat(location.getNextRefreshAt()).isEqualTo(retryAt);

        assertThat(repository
            .findTop20ByNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                failedAt.plusSeconds(300)
            ))
            .extracting(WeatherLocation::getCellId)
            .doesNotContain("cell-retry");

        assertThat(repository
            .findTop20ByNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(retryAt))
            .extracting(WeatherLocation::getCellId)
            .contains("cell-retry");
    }

    @Test
    @DisplayName("reserveInitialCollection은 최초 수집 권한을 한 번만 예약한다")
    void reserveInitialCollectionOnlyOnce() {

        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        Instant reservedUntil = now.plusSeconds(300);

        WeatherLocation location = new WeatherLocation(
            "cell-reservation",
            37.5665,
            126.9780,
            now
        );

        repository.saveAndFlush(location);

        UUID locationId = location.getId();

        entityManager.clear();

        int firstResult = repository.reserveInitialCollection(
            locationId,
            now,
            reservedUntil
        );

        int secondResult = repository.reserveInitialCollection(
            locationId,
            now,
            reservedUntil
        );

        entityManager.clear();

        WeatherLocation updated = repository.findById(locationId)
            .orElseThrow();

        assertThat(firstResult).isEqualTo(1);
        assertThat(secondResult).isZero();
        assertThat(updated.getNextRefreshAt()).isEqualTo(reservedUntil);
        assertThat(updated.getLastCollectedAt()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("reserveInitialCollection은 동시 요청 중 하나만 예약에 성공한다")
    void reserveInitialCollectionAllowsOnlyOneConcurrentRequest()
        throws Exception {

        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        Instant reservedUntil = now.plusSeconds(300);

        WeatherLocation location = new WeatherLocation(
            "cell-concurrent-" + UUID.randomUUID().toString().substring(0, 8),
            37.5665,
            126.9780,
            now
        );

        repository.saveAndFlush(location);

        UUID locationId = location.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> reserveTask = () -> {
            ready.countDown();

            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 실행 대기 시간 초과");
            }

            return repository.reserveInitialCollection(
                locationId,
                now,
                reservedUntil
            );
        };

        try {
            Future<Integer> first = executor.submit(reserveTask);
            Future<Integer> second = executor.submit(reserveTask);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            int firstResult = first.get(10, TimeUnit.SECONDS);
            int secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstResult, secondResult))
                .containsExactlyInAnyOrder(1, 0);

            WeatherLocation updated = repository.findById(locationId)
                .orElseThrow();

            assertThat(updated.getNextRefreshAt())
                .isEqualTo(reservedUntil);
        } finally {
            executor.shutdownNow();
            repository.deleteById(locationId);
        }
    }
}