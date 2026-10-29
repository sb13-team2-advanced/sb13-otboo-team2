package codeit.ootd.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import jakarta.persistence.EntityManager;
import java.time.Duration;
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
    @DisplayName("셀 ID로 날씨 수집 위치를 조회한다")
    void findWeatherLocationByCellId() {
        String cellId = "8828308281fffff";

        WeatherLocation location = new WeatherLocation(
            cellId,
            37.5665,
            126.9780,
            Instant.now().plusSeconds(3600)
        );

        repository.saveAndFlush(location);

        WeatherLocation found = repository.findByCellId(cellId)
            .orElseThrow();

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
        Instant reservedUntil = now.plus(Duration.ofMinutes(5));
        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();

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
            reservedUntil,
            firstToken
        );

        int secondResult = repository.reserveInitialCollection(
            locationId,
            now,
            reservedUntil,
            secondToken
        );

        entityManager.clear();

        WeatherLocation updated = repository.findById(locationId)
            .orElseThrow();

        assertThat(firstResult).isEqualTo(1);
        assertThat(secondResult).isZero();
        assertThat(updated.getNextRefreshAt()).isEqualTo(reservedUntil);
        assertThat(updated.getLastCollectedAt()).isNull();
        assertThat(updated.isInitialCollectionInProgress()).isTrue();
        assertThat(updated.getInitialCollectionToken()).isEqualTo(firstToken);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("reserveInitialCollection은 동시 요청 중 하나만 예약에 성공한다")
    void reserveInitialCollectionAllowsOnlyOneConcurrentRequest()
        throws Exception {

        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        Instant reservedUntil = now.plus(Duration.ofMinutes(5));

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
                reservedUntil,
                UUID.randomUUID()
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

            assertThat(updated.getNextRefreshAt()).isEqualTo(reservedUntil);
            assertThat(updated.isInitialCollectionInProgress()).isTrue();
            assertThat(updated.getInitialCollectionToken()).isNotNull();
        } finally {
            executor.shutdownNow();
            repository.deleteById(locationId);
        }
    }

    @Test
    @DisplayName("최초 수집 성공 시 수집 중 상태를 해제하고 다음 갱신을 예약한다")
    void completeCollectionClearsInitialCollectionInProgress() {
        Instant now = Instant.parse("2026-10-29T06:00:00Z");
        Instant reservedUntil = now.plus(Duration.ofMinutes(5));
        Instant completedAt = now.plusSeconds(30);
        Instant nextRefreshAt = completedAt.plus(Duration.ofHours(2));
        UUID token = UUID.randomUUID();

        WeatherLocation location = new WeatherLocation(
            "cell-complete-" + UUID.randomUUID().toString().substring(0, 8),
            37.5665,
            126.9780,
            now
        );

        repository.saveAndFlush(location);
        UUID locationId = location.getId();

        entityManager.clear();

        int reservedCount = repository.reserveInitialCollection(
            locationId,
            now,
            reservedUntil,
            token
        );

        assertThat(reservedCount).isEqualTo(1);

        entityManager.clear();

        WeatherLocation collecting = repository.findById(locationId)
            .orElseThrow();

        assertThat(collecting.isInitialCollectionInProgress()).isTrue();
        assertThat(collecting.getInitialCollectionToken()).isEqualTo(token);

        int completedCount = repository.completeInitialCollection(
            locationId,
            token,
            completedAt,
            nextRefreshAt
        );

        assertThat(completedCount).isEqualTo(1);

        entityManager.clear();

        WeatherLocation completed = repository.findById(locationId)
            .orElseThrow();

        assertThat(completed.isInitialCollectionInProgress()).isFalse();
        assertThat(completed.getInitialCollectionToken()).isNull();
        assertThat(completed.getLastCollectedAt()).isEqualTo(completedAt);
        assertThat(completed.getNextRefreshAt()).isEqualTo(nextRefreshAt);
    }

    @Test
    @DisplayName("최초 수집 실패 시 수집 중 상태를 해제하고 10분 뒤 재시도를 예약한다")
    void scheduleRetryClearsInitialCollectionInProgress() {
        Instant now = Instant.parse("2026-10-29T06:00:00Z");
        Instant reservedUntil = now.plus(Duration.ofMinutes(5));
        Instant failedAt = now.plusSeconds(30);
        Instant retryAt = failedAt.plus(Duration.ofMinutes(10));
        UUID token = UUID.randomUUID();

        WeatherLocation location = new WeatherLocation(
            "cell-fail-" + UUID.randomUUID().toString().substring(0, 8),
            37.5665,
            126.9780,
            now
        );

        repository.saveAndFlush(location);
        UUID locationId = location.getId();

        entityManager.clear();

        int reservedCount = repository.reserveInitialCollection(
            locationId,
            now,
            reservedUntil,
            token
        );

        assertThat(reservedCount).isEqualTo(1);

        entityManager.clear();

        WeatherLocation collecting = repository.findById(locationId)
            .orElseThrow();

        assertThat(collecting.isInitialCollectionInProgress()).isTrue();
        assertThat(collecting.getInitialCollectionToken()).isEqualTo(token);

        int retriedCount = repository.scheduleInitialCollectionRetry(
            locationId,
            token,
            retryAt
        );

        assertThat(retriedCount).isEqualTo(1);

        entityManager.clear();

        WeatherLocation failed = repository.findById(locationId)
            .orElseThrow();

        assertThat(failed.isInitialCollectionInProgress()).isFalse();
        assertThat(failed.getInitialCollectionToken()).isNull();
        assertThat(failed.getLastCollectedAt()).isNull();
        assertThat(failed.getNextRefreshAt()).isEqualTo(retryAt);
    }

    @Test
    @DisplayName("최초 수집 예약이 만료되면 다른 요청이 다시 예약할 수 있다")
    void reserveInitialCollectionAgainAfterExpiration() {
        Instant now = Instant.parse("2026-10-29T06:00:00Z");
        Instant firstReservedUntil = now.plus(Duration.ofMinutes(5));
        Instant secondReservedUntil = now.plus(Duration.ofMinutes(10));
        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();

        WeatherLocation location = new WeatherLocation(
            "cell-exp-" + UUID.randomUUID().toString().substring(0, 8),
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
            firstReservedUntil,
            firstToken
        );

        int beforeExpirationResult = repository.reserveInitialCollection(
            locationId,
            now.plus(Duration.ofMinutes(4)),
            now.plus(Duration.ofMinutes(9)),
            UUID.randomUUID()
        );

        int afterExpirationResult = repository.reserveInitialCollection(
            locationId,
            firstReservedUntil,
            secondReservedUntil,
            secondToken
        );

        entityManager.clear();

        WeatherLocation updated = repository.findById(locationId)
            .orElseThrow();

        assertThat(firstResult).isEqualTo(1);
        assertThat(beforeExpirationResult).isZero();
        assertThat(afterExpirationResult).isEqualTo(1);
        assertThat(updated.isInitialCollectionInProgress()).isTrue();
        assertThat(updated.getInitialCollectionToken()).isEqualTo(secondToken);
        assertThat(updated.getLastCollectedAt()).isNull();
        assertThat(updated.getNextRefreshAt()).isEqualTo(secondReservedUntil);
    }

    @Test
    @DisplayName("이전 수집 완료가 새로운 수집 예약 상태를 덮어쓰지 않는다")
    void previousCollectionDoesNotOverwriteNewReservation() {
        Instant now = Instant.parse("2026-10-29T06:00:00Z");
        Instant firstReservedUntil = now.plus(Duration.ofMinutes(5));
        Instant secondReservedUntil = now.plus(Duration.ofMinutes(10));
        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();

        WeatherLocation location = new WeatherLocation(
            "cell-overlap-" + UUID.randomUUID().toString().substring(0, 8),
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
            firstReservedUntil,
            firstToken
        );

        int secondResult = repository.reserveInitialCollection(
            locationId,
            firstReservedUntil,
            secondReservedUntil,
            secondToken
        );

        int oldCompletionResult = repository.completeInitialCollection(
            locationId,
            firstToken,
            now.plus(Duration.ofMinutes(6)),
            now.plus(Duration.ofHours(2))
        );

        entityManager.clear();

        WeatherLocation updated = repository.findById(locationId)
            .orElseThrow();

        assertThat(firstResult).isEqualTo(1);
        assertThat(secondResult).isEqualTo(1);
        assertThat(oldCompletionResult).isZero();

        assertThat(updated.isInitialCollectionInProgress()).isTrue();
        assertThat(updated.getInitialCollectionToken()).isEqualTo(secondToken);
        assertThat(updated.getLastCollectedAt()).isNull();
        assertThat(updated.getNextRefreshAt()).isEqualTo(secondReservedUntil);
    }

    @Test
    @DisplayName("이전 수집 실패가 새로운 수집 예약 상태를 덮어쓰지 않는다")
    void previousCollectionFailureDoesNotOverwriteNewReservation() {
        Instant now = Instant.parse("2026-10-29T06:00:00Z");
        Instant firstReservedUntil = now.plus(Duration.ofMinutes(5));
        Instant secondReservedUntil = now.plus(Duration.ofMinutes(10));
        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();

        WeatherLocation location = new WeatherLocation(
            "cell-old-fail-" + UUID.randomUUID().toString().substring(0, 8),
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
            firstReservedUntil,
            firstToken
        );

        int secondResult = repository.reserveInitialCollection(
            locationId,
            firstReservedUntil,
            secondReservedUntil,
            secondToken
        );

        int oldRetryResult = repository.scheduleInitialCollectionRetry(
            locationId,
            firstToken,
            now.plus(Duration.ofMinutes(16))
        );

        entityManager.clear();

        WeatherLocation updated = repository.findById(locationId)
            .orElseThrow();

        assertThat(firstResult).isEqualTo(1);
        assertThat(secondResult).isEqualTo(1);
        assertThat(oldRetryResult).isZero();

        assertThat(updated.isInitialCollectionInProgress()).isTrue();
        assertThat(updated.getInitialCollectionToken()).isEqualTo(secondToken);
        assertThat(updated.getLastCollectedAt()).isNull();
        assertThat(updated.getNextRefreshAt()).isEqualTo(secondReservedUntil);
    }

    @Test
    @DisplayName("Batch는 최초 수집을 완료하고 갱신 시각이 지난 위치만 조회한다")
    void findOnlyCollectedLocationsDueForBatch() {
        Instant now = Instant.parse("2026-10-29T06:00:00Z");

        WeatherLocation newLocation = new WeatherLocation(
            "cell-batch-new",
            37.5665,
            126.9780,
            now.minus(Duration.ofMinutes(10))
        );

        WeatherLocation dueLocation = new WeatherLocation(
            "cell-batch-due",
            37.5700,
            126.9800,
            now.minus(Duration.ofHours(3))
        );

        dueLocation.completeCollection(
            now.minus(Duration.ofHours(3)),
            now.minus(Duration.ofHours(1))
        );

        WeatherLocation futureLocation = new WeatherLocation(
            "cell-batch-future",
            37.5800,
            126.9900,
            now.minus(Duration.ofHours(1))
        );

        futureLocation.completeCollection(
            now.minus(Duration.ofHours(1)),
            now.plus(Duration.ofHours(1))
        );

        repository.saveAllAndFlush(
            List.of(newLocation, dueLocation, futureLocation)
        );

        entityManager.clear();

        List<WeatherLocation> result =
            repository.findTop20ByLastCollectedAtIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(
                now
            );

        assertThat(result)
            .extracting(WeatherLocation::getCellId)
            .containsExactly("cell-batch-due");
    }
}
