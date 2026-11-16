package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherQueryServiceTest {

    @Mock
    private WeatherLocationService weatherLocationService;

    @Mock
    private WeatherForecastCollector weatherForecastCollector;

    @Mock
    private WeatherService weatherService;

    @Mock
    private WeatherLocationRepository weatherLocationRepository;

    @Mock
    private Clock clock;

    @Mock
    private WeatherLocation location;

    @InjectMocks
    private WeatherQueryService weatherQueryService;

    @Test
    @DisplayName("최초 수집 시 Forecast5와 One Call을 수집하고 6일간 집계한다")
    void getWeathersCollectsInitialWeather() {
        double latitude = 37.5665;
        double longitude = 126.9780;
        UUID locationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-28T09:00:00Z");

        Clock fixedClock = Clock.fixed(
            now,
            ZoneId.of("Asia/Seoul")
        );

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(location.getLastCollectedAt()).thenReturn(null);
        when(location.getNextRefreshAt()).thenReturn(now);
        when(location.getId()).thenReturn(locationId);
        when(clock.instant()).thenReturn(now);
        when(clock.withZone(ZoneId.of("Asia/Seoul")))
            .thenReturn(fixedClock);

        when(weatherLocationRepository.reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            any(UUID.class)
        )).thenReturn(1);

        weatherQueryService.getWeathers(latitude, longitude);

        ArgumentCaptor<UUID> tokenCaptor =
            ArgumentCaptor.forClass(UUID.class);

        verify(weatherLocationRepository).reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            tokenCaptor.capture()
        );

        verify(weatherForecastCollector).collectForecasts(location);
        verify(weatherForecastCollector).collectHistoricalForecasts(location);

        LocalDate today = LocalDate.of(2026, 10, 28);

        for (int day = 0; day < 6; day++) {
            verify(weatherService).calculateAndSaveDailyWeather(
                location,
                today.plusDays(day)
            );
        }

        verify(weatherLocationRepository).completeInitialCollection(
            locationId,
            tokenCaptor.getValue(),
            now,
            now.plus(Duration.ofHours(2))
        );

        verify(weatherService).getWeathers(latitude, longitude);
    }

    @Test
    @DisplayName("One Call 수집에 실패해도 Forecast5 데이터로 일별 집계를 완료한다")
    void getWeathersContinuesWhenOneCallFails() {
        double latitude = 37.5665;
        double longitude = 126.9780;
        UUID locationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-13T09:00:00Z");

        Clock fixedClock = Clock.fixed(
            now,
            ZoneId.of("Asia/Seoul")
        );

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(location.getLastCollectedAt()).thenReturn(null);
        when(location.getNextRefreshAt()).thenReturn(now);
        when(location.getId()).thenReturn(locationId);
        when(clock.instant()).thenReturn(now);
        when(clock.withZone(ZoneId.of("Asia/Seoul")))
            .thenReturn(fixedClock);

        when(weatherLocationRepository.reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            any(UUID.class)
        )).thenReturn(1);

        doThrow(new IllegalStateException("One Call 호출 제한 초과"))
            .when(weatherForecastCollector)
            .collectHistoricalForecasts(location);

        weatherQueryService.getWeathers(latitude, longitude);

        verify(weatherForecastCollector).collectForecasts(location);
        verify(weatherForecastCollector).collectHistoricalForecasts(location);

        LocalDate today = LocalDate.of(2026, 11, 13);

        for (int day = 0; day < 6; day++) {
            verify(weatherService).calculateAndSaveDailyWeather(
                location,
                today.plusDays(day)
            );
        }

        ArgumentCaptor<UUID> tokenCaptor =
            ArgumentCaptor.forClass(UUID.class);

        verify(weatherLocationRepository).reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            tokenCaptor.capture()
        );

        verify(weatherLocationRepository).completeInitialCollection(
            locationId,
            tokenCaptor.getValue(),
            now,
            now.plus(Duration.ofHours(2))
        );

        verify(weatherLocationRepository, never())
            .scheduleInitialCollectionRetry(
                any(UUID.class),
                any(UUID.class),
                any(Instant.class)
            );

        verify(weatherService).getWeathers(latitude, longitude);
    }

    @Test
    @DisplayName("기존 수집 위치에서는 최초 수집을 다시 실행하지 않는다")
    void getWeathersSkipsInitialCollection() {
        double latitude = 37.5665;
        double longitude = 126.9780;

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(location.getLastCollectedAt())
            .thenReturn(Instant.parse("2026-10-28T06:00:00Z"));

        List<WeatherDto> result =
            weatherQueryService.getWeathers(latitude, longitude);

        assertThat(result).isEmpty();

        verifyNoInteractions(weatherForecastCollector);
        verifyNoInteractions(weatherLocationRepository);
        verify(weatherService).getWeathers(latitude, longitude);
    }

    @Test
    @DisplayName("Forecast5 최초 수집에 실패하면 10분 뒤 재시도를 예약한다")
    void getWeathersSchedulesRetryWhenCollectionFails() {
        double latitude = 37.5665;
        double longitude = 126.9780;
        UUID locationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-28T09:00:00Z");

        Clock fixedClock = Clock.fixed(
            now,
            ZoneId.of("Asia/Seoul")
        );

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(location.getLastCollectedAt()).thenReturn(null);
        when(location.getNextRefreshAt()).thenReturn(now);
        when(location.getId()).thenReturn(locationId);
        when(clock.instant()).thenReturn(now);
        when(clock.withZone(ZoneId.of("Asia/Seoul")))
            .thenReturn(fixedClock);

        when(weatherLocationRepository.reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            any(UUID.class)
        )).thenReturn(1);

        doThrow(new RuntimeException("OWM API 호출 실패"))
            .when(weatherForecastCollector)
            .collectForecasts(location);

        assertThatThrownBy(
            () -> weatherQueryService.getWeathers(latitude, longitude)
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessage("OWM API 호출 실패");

        ArgumentCaptor<UUID> tokenCaptor =
            ArgumentCaptor.forClass(UUID.class);

        verify(weatherLocationRepository).reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            tokenCaptor.capture()
        );

        verify(weatherLocationRepository).scheduleInitialCollectionRetry(
            locationId,
            tokenCaptor.getValue(),
            now.plus(Duration.ofMinutes(10))
        );

        verify(weatherForecastCollector, never())
            .collectHistoricalForecasts(location);

        verify(weatherService, never())
            .getWeathers(latitude, longitude);
    }

    @Test
    @DisplayName("재시도 예정 시각 전에는 날씨를 다시 수집하지 않는다")
    void getWeathersSkipsCollectionBeforeRetryTime() {
        double latitude = 37.5665;
        double longitude = 126.9780;
        Instant now = Instant.parse("2026-10-28T09:00:00Z");

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(location.getLastCollectedAt()).thenReturn(null);
        when(location.getNextRefreshAt())
            .thenReturn(now.plus(Duration.ofMinutes(10)));
        when(clock.instant()).thenReturn(now);

        List<WeatherDto> result =
            weatherQueryService.getWeathers(latitude, longitude);

        assertThat(result).isEmpty();

        verifyNoInteractions(weatherForecastCollector);
        verifyNoInteractions(weatherLocationRepository);
        verify(weatherService).getWeathers(latitude, longitude);
    }

    @Test
    @DisplayName("다른 요청이 최초 수집 권한을 확보했다면 중복 수집하지 않는다")
    void getWeathersSkipsCollectionWhenReservationFails() {
        double latitude = 37.5665;
        double longitude = 126.9780;
        UUID locationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-28T09:00:00Z");

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(location.getLastCollectedAt()).thenReturn(null);
        when(location.getNextRefreshAt()).thenReturn(now);
        when(location.getId()).thenReturn(locationId);
        when(clock.instant()).thenReturn(now);

        when(weatherLocationRepository.reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            any(UUID.class)
        )).thenReturn(0);

        when(weatherLocationRepository.findById(locationId))
            .thenReturn(Optional.empty());

        List<WeatherDto> result =
            weatherQueryService.getWeathers(latitude, longitude);

        assertThat(result).isEmpty();

        verify(weatherLocationRepository).reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            any(UUID.class)
        );

        verify(weatherLocationRepository).findById(locationId);
        verifyNoInteractions(weatherForecastCollector);
        verify(weatherService).getWeathers(latitude, longitude);
    }

    @Test
    @DisplayName("다른 요청의 최초 수집이 완료되면 저장된 날씨를 반환한다")
    void getWeathersReturnsWeatherAfterConcurrentCollection() {
        double latitude = 37.5665;
        double longitude = 126.9780;
        UUID locationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-29T06:00:00Z");

        WeatherLocation latest = mock(WeatherLocation.class);
        WeatherDto weatherDto = mock(WeatherDto.class);

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(location.getLastCollectedAt()).thenReturn(null);
        when(location.getNextRefreshAt()).thenReturn(now);
        when(location.getId()).thenReturn(locationId);
        when(clock.instant()).thenReturn(now);

        when(weatherLocationRepository.reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            any(UUID.class)
        )).thenReturn(0);

        when(weatherLocationRepository.findById(locationId))
            .thenReturn(Optional.of(latest));

        when(latest.getLastCollectedAt())
            .thenReturn(now.plusSeconds(1));

        when(weatherService.getWeathers(latitude, longitude))
            .thenReturn(List.of(weatherDto));

        List<WeatherDto> result =
            weatherQueryService.getWeathers(latitude, longitude);

        assertThat(result).containsExactly(weatherDto);

        verify(weatherLocationRepository).findById(locationId);
        verifyNoInteractions(weatherForecastCollector);
        verify(weatherService).getWeathers(latitude, longitude);
    }

    @Test
    @DisplayName("다른 요청의 최초 수집이 3초 안에 끝나지 않으면 빈 목록을 반환한다")
    void getWeathersReturnsEmptyWhenInitialCollectionTimesOut() {
        double latitude = 37.5665;
        double longitude = 126.9780;
        UUID locationId = UUID.randomUUID();
        Instant now = Instant.now();

        WeatherLocation latest = mock(WeatherLocation.class);

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(location.getLastCollectedAt()).thenReturn(null);
        when(location.getNextRefreshAt()).thenReturn(now);
        when(location.getId()).thenReturn(locationId);
        when(clock.instant()).thenReturn(now);

        when(weatherLocationRepository.reserveInitialCollection(
            eq(locationId),
            eq(now),
            eq(now.plus(Duration.ofMinutes(5))),
            any(UUID.class)
        )).thenReturn(0);

        when(weatherLocationRepository.findById(locationId))
            .thenReturn(Optional.of(latest));

        when(latest.getLastCollectedAt()).thenReturn(null);
        when(latest.isInitialCollectionInProgress()).thenReturn(true);

        when(weatherService.getWeathers(latitude, longitude))
            .thenReturn(List.of());

        List<WeatherDto> result =
            weatherQueryService.getWeathers(latitude, longitude);

        assertThat(result).isEmpty();

        verify(weatherLocationRepository, times(4)).findById(locationId);
        verifyNoInteractions(weatherForecastCollector);
        verify(weatherService).getWeathers(latitude, longitude);
    }

    @Test
    @DisplayName("재시도 대기 중에는 불필요한 최초 수집 완료 대기를 하지 않는다")
    void getWeathersDoesNotWaitDuringRetryInterval() {
        double latitude = 37.5665;
        double longitude = 126.9780;
        Instant now = Instant.parse("2026-10-29T06:07:00Z");

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);
        when(location.getLastCollectedAt()).thenReturn(null);
        when(location.getNextRefreshAt())
            .thenReturn(now.plus(Duration.ofMinutes(3)));
        when(clock.instant()).thenReturn(now);

        List<WeatherDto> result =
            weatherQueryService.getWeathers(latitude, longitude);

        assertThat(result).isEmpty();

        verifyNoInteractions(weatherLocationRepository);
        verifyNoInteractions(weatherForecastCollector);
        verify(weatherService).getWeathers(latitude, longitude);
    }
}
