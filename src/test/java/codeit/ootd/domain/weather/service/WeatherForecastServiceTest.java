package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.service.WeatherChangeDetector.PrecipitationType;
import codeit.ootd.infra.weather.OwmHourlyForecastConverter.ThreeHourForecast;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WeatherForecastServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-11-05T00:00:00Z");

    @Mock
    private WeatherForecastRepository weatherForecastRepository;

    @Mock
    private Clock clock;

    @Mock
    private WeatherNotificationRecipientFinder recipientFinder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private WeatherChangeDetector weatherChangeDetector =
        new WeatherChangeDetector();

    @Spy
    private WeatherNotificationMessageBuilder weatherNotificationMessageBuilder =
        new WeatherNotificationMessageBuilder();

    @InjectMocks
    private WeatherForecastService weatherForecastService;

    @Test
    @DisplayName("기존 예보가 없으면 새로운 예보를 저장하고 알림을 생성하지 않는다")
    void saveForecastsCreatesNewForecast() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.plusSeconds(3600);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(forecastItem(forecastAt, 18.5, 800))
        );

        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, List.of(forecastAt)
        )).thenReturn(List.of());

        when(clock.instant()).thenReturn(NOW);

        weatherForecastService.saveForecasts(location, response);

        ArgumentCaptor<WeatherForecast> captor =
            ArgumentCaptor.forClass(WeatherForecast.class);

        verify(weatherForecastRepository).save(captor.capture());
        verify(weatherForecastRepository).findByLocationIdAndForecastAtIn(
            locationId, List.of(forecastAt)
        );

        WeatherForecast savedForecast = captor.getValue();

        assertThat(savedForecast.getLocationId()).isEqualTo(locationId);
        assertThat(savedForecast.getForecastAt()).isEqualTo(forecastAt);
        assertThat(savedForecast.getTemperature()).isEqualTo(18.5);
        assertThat(savedForecast.getHumidity()).isEqualTo((short) 65);
        assertThat(savedForecast.getWeatherCode()).isEqualTo(800);
        assertThat(savedForecast.getPrecipitationProbability()).isEqualTo(0.2);
        assertThat(savedForecast.getRainAmount()).isZero();
        assertThat(savedForecast.getSnowAmount()).isZero();
        assertThat(savedForecast.getWindSpeed()).isEqualTo(2.5);

        verifyNoInteractions(weatherNotificationMessageBuilder);
        verifyNoInteractions(recipientFinder, eventPublisher);
    }

    @Test
    @DisplayName("기존 예보가 있으면 최신 예보로 갱신한다")
    void saveForecastsUpdatesExistingForecast() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.plusSeconds(3600);
        Instant oldCollectedAt = NOW.minusSeconds(86400);

        WeatherForecast existingForecast = new WeatherForecast(
            locationId,
            forecastAt,
            oldCollectedAt,
            18.5,
            (short) 65,
            800,
            0.2,
            0.0,
            0.0,
            2.5
        );

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(new OwmForecastResponse.ForecastItem(
                forecastAt.getEpochSecond(),
                new OwmForecastResponse.Main(15.0, (short) 75),
                List.of(new OwmForecastResponse.WeatherCondition(500)),
                0.8,
                new OwmForecastResponse.Precipitation(1.3),
                null,
                new OwmForecastResponse.Wind(3.5)
            ))
        );

        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, List.of(forecastAt)
        )).thenReturn(List.of(existingForecast));

        when(clock.instant()).thenReturn(NOW);

        weatherForecastService.saveForecasts(location, response);

        verify(weatherForecastRepository).save(existingForecast);

        assertThat(existingForecast.getTemperature()).isEqualTo(15.0);
        assertThat(existingForecast.getHumidity()).isEqualTo((short) 75);
        assertThat(existingForecast.getWeatherCode()).isEqualTo(500);
        assertThat(existingForecast.getPrecipitationProbability()).isEqualTo(0.8);
        assertThat(existingForecast.getRainAmount()).isEqualTo(1.3);
        assertThat(existingForecast.getCollectedAt()).isAfter(oldCollectedAt);
    }

    @ParameterizedTest
    @DisplayName("수집 시각 이후 6시간 이내의 예보 변화만 알림 대상으로 판별한다")
    @CsvSource({
        "-1, false",
        "0, false",
        "1, true",
        "21600, true",
        "21601, false"
    })
    void saveForecastsDetectsChangesWithinSixHours(
        long secondsAfterCollection,
        boolean shouldNotify
    ) {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.plusSeconds(secondsAfterCollection);

        WeatherForecast existingForecast =
            previousForecast(locationId, forecastAt, 20.0, 800);

        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, List.of(forecastAt)
        )).thenReturn(List.of(existingForecast));

        when(clock.instant()).thenReturn(NOW);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(forecastItem(forecastAt, 26.0, 800))
        );

        weatherForecastService.saveForecasts(location, response);

        verify(weatherForecastRepository).save(existingForecast);

        if (shouldNotify) {
            verify(weatherNotificationMessageBuilder).build(
                true,
                false,
                Set.of()
            );
        } else {
            verifyNoInteractions(weatherNotificationMessageBuilder);
        }
    }

    @Test
    @DisplayName("여러 시간대의 예보 변화는 하나의 알림 메시지로 병합한다")
    void saveForecastsMergesChangesIntoSingleMessage() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant firstAt = NOW.plusSeconds(3600);
        Instant secondAt = NOW.plusSeconds(7200);
        Instant thirdAt = NOW.plusSeconds(10800);

        WeatherForecast first =
            previousForecast(locationId, firstAt, 20.0, 800);
        WeatherForecast second =
            previousForecast(locationId, secondAt, 20.0, 800);
        WeatherForecast third =
            previousForecast(locationId, thirdAt, 20.0, 800);

        List<Instant> forecastAts =
            List.of(thirdAt, firstAt, secondAt);

        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, forecastAts
        )).thenReturn(List.of(first, second, third));

        when(clock.instant()).thenReturn(NOW);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(
                forecastItem(thirdAt, 14.0, 600),
                forecastItem(firstAt, 26.0, 500),
                forecastItem(secondAt, 25.0, 500)
            )
        );

        weatherForecastService.saveForecasts(location, response);

        verify(weatherForecastRepository).findByLocationIdAndForecastAtIn(
            locationId, forecastAts
        );

        verify(weatherForecastRepository).save(first);
        verify(weatherForecastRepository).save(second);
        verify(weatherForecastRepository).save(third);

        verify(weatherNotificationMessageBuilder).build(
            true,
            true,
            Set.of(
                PrecipitationType.RAIN,
                PrecipitationType.SNOW
            )
        );
    }

    @Test
    @DisplayName("기존 예보와 날씨 변화가 없으면 알림 메시지를 생성하지 않는다")
    void saveForecastsDoesNotNotifyWhenWeatherIsUnchanged() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.plusSeconds(3600);

        WeatherForecast existingForecast =
            previousForecast(locationId, forecastAt, 20.0, 800);

        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, List.of(forecastAt)
        )).thenReturn(List.of(existingForecast));

        when(clock.instant()).thenReturn(NOW);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(forecastItem(forecastAt, 20.0, 800))
        );

        weatherForecastService.saveForecasts(location, response);

        verify(weatherForecastRepository).save(existingForecast);
        verifyNoInteractions(weatherNotificationMessageBuilder);
        verifyNoInteractions(recipientFinder, eventPublisher);
    }

    @Test
    @DisplayName("40개 예보를 저장할 때 기존 예보를 한 번만 조회한다")
    void saveForecastsLoadsFortyForecastsInOneQuery() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        List<OwmForecastResponse.ForecastItem> items = new ArrayList<>();
        List<Instant> forecastAts = new ArrayList<>();

        for (int i = 1; i <= 40; i++) {
            Instant forecastAt = NOW.plusSeconds(i * 10800L);
            forecastAts.add(forecastAt);
            items.add(forecastItem(forecastAt, 18.0, 800));
        }

        when(clock.instant()).thenReturn(NOW);
        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, forecastAts
        )).thenReturn(List.of());

        weatherForecastService.saveForecasts(
            location,
            new OwmForecastResponse(items)
        );

        verify(weatherForecastRepository, times(1))
            .findByLocationIdAndForecastAtIn(locationId, forecastAts);

        verify(weatherForecastRepository, times(40))
            .save(any(WeatherForecast.class));

        verify(weatherForecastRepository, never())
            .findByLocationIdAndForecastAt(any(), any());

        verifyNoInteractions(recipientFinder, eventPublisher);
    }

    @Test
    @DisplayName("동일 시각의 예보가 중복되면 한 번 조회하고 같은 엔티티를 갱신한다")
    void saveForecastsReusesForecastForDuplicateTime() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.plusSeconds(3600);

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(
                forecastItem(forecastAt, 18.0, 800),
                forecastItem(forecastAt, 18.0, 800)
            )
        );

        when(clock.instant()).thenReturn(NOW);
        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, List.of(forecastAt)
        )).thenReturn(List.of());

        weatherForecastService.saveForecasts(location, response);

        ArgumentCaptor<WeatherForecast> captor =
            ArgumentCaptor.forClass(WeatherForecast.class);

        verify(weatherForecastRepository, times(1))
            .findByLocationIdAndForecastAtIn(
                locationId, List.of(forecastAt)
            );

        verify(weatherForecastRepository, times(2))
            .save(captor.capture());

        assertThat(captor.getAllValues().get(0))
            .isSameAs(captor.getAllValues().get(1));

        verifyNoInteractions(recipientFinder, eventPublisher);
    }

    @Test
    @DisplayName("예보 목록이 비어 있으면 조회와 저장을 실행하지 않는다")
    void saveForecastsSkipsEmptyResponse() {
        WeatherLocation location = mock(WeatherLocation.class);

        when(clock.instant()).thenReturn(NOW);

        weatherForecastService.saveForecasts(
            location,
            new OwmForecastResponse(List.of())
        );

        verifyNoInteractions(
            weatherForecastRepository,
            weatherChangeDetector,
            weatherNotificationMessageBuilder,
            recipientFinder,
            eventPublisher
        );
    }

    @Test
    @DisplayName("날씨가 변경되면 같은 지역의 사용자별로 알림 이벤트를 발행한다")
    void saveForecastsPublishesNotificationForEachRecipient() {
        UUID locationId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        String cellId = "target-cell";

        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);
        when(location.getCellId()).thenReturn(cellId);

        Instant forecastAt = NOW.plusSeconds(3600);

        WeatherForecast existingForecast =
            previousForecast(locationId, forecastAt, 20.0, 800);

        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, List.of(forecastAt)
        )).thenReturn(List.of(existingForecast));

        when(clock.instant()).thenReturn(NOW);
        when(recipientFinder.findRecipientIds(cellId))
            .thenReturn(Set.of(firstUserId, secondUserId));

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(forecastItem(forecastAt, 26.0, 500))
        );

        weatherForecastService.saveForecasts(location, response);

        ArgumentCaptor<NotificationRequestedEvent> captor =
            ArgumentCaptor.forClass(NotificationRequestedEvent.class);

        verify(eventPublisher, times(2)).publishEvent(captor.capture());

        List<NotificationRequestedEvent> events = captor.getAllValues();

        assertThat(events)
            .extracting(NotificationRequestedEvent::receiverId)
            .containsExactlyInAnyOrder(firstUserId, secondUserId);

        assertThat(events).allSatisfy(event -> {
            assertThat(event.title()).isEqualTo("날씨 예보가 달라졌어요!");
            assertThat(event.level()).isEqualTo(NotificationLevel.INFO);
            assertThat(event.content()).contains("비가 올", "높아졌어요");
        });
    }

    @Test
    @DisplayName("과거 예보가 없으면 강수확률 null을 유지하여 신규 저장하고 알림을 발송하지 않는다")
    void saveHistoricalForecastsCreatesNewForecast() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.minusSeconds(10800);

        ThreeHourForecast item = new ThreeHourForecast(
            forecastAt,
            12.5,
            (short) 75,
            500,
            null,
            0.9,
            0.0,
            2.3
        );

        when(clock.instant()).thenReturn(NOW);
        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, List.of(forecastAt)
        )).thenReturn(List.of());

        weatherForecastService.saveHistoricalForecasts(
            location,
            List.of(item)
        );

        ArgumentCaptor<WeatherForecast> captor =
            ArgumentCaptor.forClass(WeatherForecast.class);

        verify(weatherForecastRepository).save(captor.capture());

        WeatherForecast saved = captor.getValue();

        assertThat(saved.getLocationId()).isEqualTo(locationId);
        assertThat(saved.getForecastAt()).isEqualTo(forecastAt);
        assertThat(saved.getCollectedAt()).isEqualTo(NOW);
        assertThat(saved.getTemperature()).isEqualTo(12.5);
        assertThat(saved.getHumidity()).isEqualTo((short) 75);
        assertThat(saved.getWeatherCode()).isEqualTo(500);
        assertThat(saved.getPrecipitationProbability()).isNull();
        assertThat(saved.getRainAmount()).isEqualTo(0.9);
        assertThat(saved.getSnowAmount()).isZero();
        assertThat(saved.getWindSpeed()).isEqualTo(2.3);

        verifyNoInteractions(
            weatherChangeDetector,
            weatherNotificationMessageBuilder,
            recipientFinder,
            eventPublisher
        );
    }

    @Test
    @DisplayName("기존 과거 예보가 있으면 새로운 관측값으로 갱신한다")
    void saveHistoricalForecastsUpdatesExistingForecast() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant forecastAt = NOW.minusSeconds(10800);
        Instant oldCollectedAt = NOW.minusSeconds(86400);

        WeatherForecast existing = new WeatherForecast(
            locationId,
            forecastAt,
            oldCollectedAt,
            10.0,
            (short) 60,
            800,
            0.2,
            0.0,
            0.0,
            1.0
        );

        ThreeHourForecast item = new ThreeHourForecast(
            forecastAt,
            15.0,
            (short) 80,
            600,
            null,
            0.5,
            1.2,
            3.0
        );

        when(clock.instant()).thenReturn(NOW);
        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, List.of(forecastAt)
        )).thenReturn(List.of(existing));

        weatherForecastService.saveHistoricalForecasts(
            location,
            List.of(item)
        );

        verify(weatherForecastRepository).save(existing);

        assertThat(existing.getCollectedAt()).isEqualTo(NOW);
        assertThat(existing.getTemperature()).isEqualTo(15.0);
        assertThat(existing.getHumidity()).isEqualTo((short) 80);
        assertThat(existing.getWeatherCode()).isEqualTo(600);
        assertThat(existing.getPrecipitationProbability()).isNull();
        assertThat(existing.getRainAmount()).isEqualTo(0.5);
        assertThat(existing.getSnowAmount()).isEqualTo(1.2);
        assertThat(existing.getWindSpeed()).isEqualTo(3.0);

        verifyNoInteractions(
            weatherChangeDetector,
            weatherNotificationMessageBuilder,
            recipientFinder,
            eventPublisher
        );
    }

    @Test
    @DisplayName("여러 과거 예보를 저장할 때 기존 데이터를 한 번만 조회한다")
    void saveHistoricalForecastsLoadsForecastsInOneQuery() {
        UUID locationId = UUID.randomUUID();
        WeatherLocation location = mock(WeatherLocation.class);
        when(location.getId()).thenReturn(locationId);

        Instant firstAt = NOW.minusSeconds(21600);
        Instant secondAt = NOW.minusSeconds(10800);

        WeatherForecast existing =
            previousForecast(locationId, firstAt, 10.0, 800);

        ThreeHourForecast first = new ThreeHourForecast(
            firstAt,
            13.0,
            (short) 70,
            500,
            null,
            0.6,
            0.0,
            2.0
        );

        ThreeHourForecast second = new ThreeHourForecast(
            secondAt,
            14.0,
            (short) 75,
            800,
            null,
            0.0,
            0.0,
            3.0
        );

        when(clock.instant()).thenReturn(NOW);
        when(weatherForecastRepository.findByLocationIdAndForecastAtIn(
            locationId, List.of(firstAt, secondAt)
        )).thenReturn(List.of(existing));

        weatherForecastService.saveHistoricalForecasts(
            location,
            List.of(first, second)
        );

        ArgumentCaptor<WeatherForecast> captor =
            ArgumentCaptor.forClass(WeatherForecast.class);

        verify(weatherForecastRepository, times(1))
            .findByLocationIdAndForecastAtIn(
                locationId, List.of(firstAt, secondAt)
            );

        verify(weatherForecastRepository, times(2))
            .save(captor.capture());

        assertThat(captor.getAllValues())
            .extracting(WeatherForecast::getForecastAt)
            .containsExactly(firstAt, secondAt);

        assertThat(existing.getTemperature()).isEqualTo(13.0);
        assertThat(existing.getWeatherCode()).isEqualTo(500);

        verify(weatherForecastRepository, never())
            .findByLocationIdAndForecastAt(any(), any());

        verifyNoInteractions(
            weatherChangeDetector,
            weatherNotificationMessageBuilder,
            recipientFinder,
            eventPublisher
        );
    }

    @Test
    @DisplayName("현재와 미래 시각의 데이터는 과거 예보로 저장하지 않는다")
    void saveHistoricalForecastsSkipsCurrentAndFutureForecasts() {
        WeatherLocation location = mock(WeatherLocation.class);

        ThreeHourForecast current = new ThreeHourForecast(
            NOW,
            15.0,
            (short) 70,
            803,
            null,
            0.0,
            0.0,
            2.0
        );

        ThreeHourForecast future = new ThreeHourForecast(
            NOW.plusSeconds(3600),
            16.0,
            (short) 75,
            500,
            0.5,
            0.3,
            0.0,
            3.0
        );

        when(clock.instant()).thenReturn(NOW);

        weatherForecastService.saveHistoricalForecasts(
            location,
            List.of(current, future)
        );

        verifyNoInteractions(
            weatherForecastRepository,
            weatherChangeDetector,
            weatherNotificationMessageBuilder,
            recipientFinder,
            eventPublisher
        );
    }

    private WeatherForecast previousForecast(
        UUID locationId,
        Instant forecastAt,
        double temperature,
        int weatherCode
    ) {
        return new WeatherForecast(
            locationId,
            forecastAt,
            NOW.minusSeconds(86400),
            temperature,
            (short) 65,
            weatherCode,
            0.2,
            0.0,
            0.0,
            2.5
        );
    }

    private OwmForecastResponse.ForecastItem forecastItem(
        Instant forecastAt,
        double temperature,
        int weatherCode
    ) {
        return new OwmForecastResponse.ForecastItem(
            forecastAt.getEpochSecond(),
            new OwmForecastResponse.Main(temperature, (short) 65),
            List.of(new OwmForecastResponse.WeatherCondition(weatherCode)),
            0.2,
            null,
            null,
            new OwmForecastResponse.Wind(2.5)
        );
    }
}
