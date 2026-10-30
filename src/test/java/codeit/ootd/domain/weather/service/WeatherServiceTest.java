package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.mapper.WeatherDtoMapper;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("일별 날씨 집계 서비스 테스트")
class WeatherServiceTest {

    private static final LocalDate WEATHER_DATE =
        LocalDate.of(2026, 10, 26);

    private static final Instant START =
        Instant.parse("2026-10-25T15:00:00Z");

    private static final Instant END =
        Instant.parse("2026-10-26T15:00:00Z");

    private final UUID locationId = UUID.randomUUID();

    @Mock
    private WeatherLocation location;

    @Mock
    private WeatherForecastRepository weatherForecastRepository;

    @Mock
    private WeatherRepository weatherRepository;

    @Mock
    private WeatherLocationService weatherLocationService;

    @Mock
    private WeatherDtoMapper weatherDtoMapper;

    @Mock
    private Clock clock;

    @InjectMocks
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        when(location.getId()).thenReturn(locationId);
    }

    @Test
    @DisplayName("예보가 없으면 일별 날씨를 저장하지 않는다")
    void shouldNotSaveWhenForecastsAreEmpty() {

        givenForecasts();

        weatherService.calculateAndSaveDailyWeather(
            location, WEATHER_DATE
        );

        verify(weatherForecastRepository)
            .findForecastsForPeriod(locationId, START, END);

        verifyNoInteractions(weatherRepository);
    }

    @Test
    @DisplayName("일별 예보를 집계하여 새로운 날씨를 저장한다")
    void shouldCalculateAndSaveNewWeather() {

        givenForecasts(
            forecast("2026-10-25T15:00:00Z",
                10.0, (short) 60, 800,
                0.1, 0.0, 0.0, 2.0),

            forecast("2026-10-25T18:00:00Z",
                14.0, (short) 70, 500,
                0.9, 1.5, 0.0, 5.0),

            forecast("2026-10-25T21:00:00Z",
                18.0, (short) 80, 600,
                0.4, 0.0, 0.4, 3.0)
        );

        when(weatherRepository.findByLocationIdAndWeatherDate(
            locationId, WEATHER_DATE
        )).thenReturn(Optional.empty());

        weatherService.calculateAndSaveDailyWeather(
            location, WEATHER_DATE
        );

        Weather saved = captureSavedWeather();

        assertThat(saved.getLocationId()).isEqualTo(locationId);
        assertThat(saved.getWeatherDate()).isEqualTo(WEATHER_DATE);
        assertThat(saved.getCalculatedAt()).isNotNull();

        assertThat(saved.getTemperatureAvg()).isEqualTo(14.0);
        assertThat(saved.getTemperatureMin()).isEqualTo(10.0);
        assertThat(saved.getTemperatureMax()).isEqualTo(18.0);

        assertThat(saved.getHumidityAvg()).isEqualTo((short) 70);
        assertThat(saved.getWindSpeed()).isEqualTo(10.0 / 3.0);

        assertThat(saved.getPrecipitationProbability())
            .isEqualTo(0.9);
        assertThat(saved.getRainAmount()).isEqualTo(1.5);
        assertThat(saved.getSnowAmount()).isEqualTo(0.4);

        assertThat(saved.getWeatherCode()).isEqualTo(500);

        assertThat(saved.getForecastSlotCount())
            .isEqualTo((short) 3);
    }

    @Test
    @DisplayName("기존 일별 날씨가 있으면 새로운 예보로 갱신한다")
    void shouldUpdateExistingWeather() {

        Instant oldCalculatedAt =
            Instant.parse("2026-10-20T00:00:00Z");

        Weather existing = new Weather(
            locationId,
            WEATHER_DATE,
            oldCalculatedAt,
            5.0,
            3.0,
            7.0,
            (short) 50,
            800,
            0.1,
            0.0,
            0.0,
            1.0,
            (short) 1
        );

        givenForecasts(
            forecast("2026-10-26T03:00:00Z",
                20.0, (short) 75, 500,
                0.8, 2.0, 0.0, 4.0)
        );

        when(weatherRepository.findByLocationIdAndWeatherDate(
            locationId, WEATHER_DATE
        )).thenReturn(Optional.of(existing));

        weatherService.calculateAndSaveDailyWeather(
            location, WEATHER_DATE
        );

        verify(weatherRepository).save(same(existing));

        assertThat(existing.getTemperatureAvg()).isEqualTo(20.0);
        assertThat(existing.getHumidityAvg()).isEqualTo((short) 75);
        assertThat(existing.getWeatherCode()).isEqualTo(500);
        assertThat(existing.getRainAmount()).isEqualTo(2.0);
        assertThat(existing.getWindSpeed()).isEqualTo(4.0);
        assertThat(existing.getForecastSlotCount())
            .isEqualTo((short) 1);
        assertThat(existing.getCalculatedAt())
            .isAfter(oldCalculatedAt);
    }

    @Test
    @DisplayName("강수 예보가 있어도 더 자주 등장한 맑음을 선택한다")
    void shouldChooseMostFrequentWeatherCode() {

        givenForecasts(
            forecast("2026-10-25T15:00:00Z",
                10.0, (short) 60, 800,
                0.0, 0.0, 0.0, 2.0),

            forecast("2026-10-25T18:00:00Z",
                12.0, (short) 60, 500,
                0.7, 1.0, 0.0, 2.0),

            forecast("2026-10-25T21:00:00Z",
                14.0, (short) 60, 800,
                0.0, 0.0, 0.0, 2.0)
        );

        when(weatherRepository.findByLocationIdAndWeatherDate(
            locationId, WEATHER_DATE
        )).thenReturn(Optional.empty());

        weatherService.calculateAndSaveDailyWeather(
            location, WEATHER_DATE
        );

        Weather saved = captureSavedWeather();

        assertThat(saved.getWeatherCode()).isEqualTo(800);

        assertThat(saved.getPrecipitationProbability())
            .isEqualTo(0.7);
        assertThat(saved.getRainAmount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("일별 강수량은 당일 00시를 제외하고 다음 날 00시를 포함한다")
    void shouldUseCorrectPrecipitationTimeRange() {

        givenForecasts(
            forecast("2026-10-25T15:00:00Z",
                10.0, (short) 60, 800,
                0.1, 50.0, 40.0, 2.0),

            forecast("2026-10-25T18:00:00Z",
                20.0, (short) 70, 500,
                0.4, 2.0, 1.0, 3.0),

            forecast("2026-10-26T12:00:00Z",
                30.0, (short) 80, 600,
                0.5, 3.0, 2.0, 4.0)
        );

        WeatherForecast nextDayStart = forecast(
            "2026-10-26T15:00:00Z",
            99.0, (short) 90, 600,
            0.9, 4.0, 3.0, 8.0
        );

        when(weatherForecastRepository.findByLocationIdAndForecastAt(
            locationId, END
        )).thenReturn(Optional.of(nextDayStart));

        when(weatherRepository.findByLocationIdAndWeatherDate(
            locationId, WEATHER_DATE
        )).thenReturn(Optional.empty());

        weatherService.calculateAndSaveDailyWeather(
            location, WEATHER_DATE
        );

        Weather saved = captureSavedWeather();

        assertThat(saved.getRainAmount()).isEqualTo(9.0);

        assertThat(saved.getSnowAmount()).isEqualTo(6.0);

        assertThat(saved.getTemperatureAvg()).isEqualTo(20.0);
        assertThat(saved.getTemperatureMax()).isEqualTo(30.0);
        assertThat(saved.getForecastSlotCount()).isEqualTo((short) 3);
        assertThat(saved.getPrecipitationProbability()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("getWeathers는 전날 데이터를 비교에 사용하고 오늘부터 날짜순으로 반환한다")
    void getWeathersReturnsDailyWeatherDtos() {

        ZoneId koreaZone = ZoneId.of("Asia/Seoul");
        Clock fixedClock = Clock.fixed(
            Instant.parse("2026-10-28T09:00:00Z"),
            koreaZone
        );

        when(clock.withZone(koreaZone)).thenReturn(fixedClock);

        double latitude = 37.5665;
        double longitude = 126.9780;

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);

        LocalDate today = LocalDate.of(2026, 10, 28);

        Weather yesterday = mock(Weather.class);
        Weather current = mock(Weather.class);
        Weather tomorrow = mock(Weather.class);

        when(yesterday.getWeatherDate()).thenReturn(today.minusDays(1));
        when(current.getWeatherDate()).thenReturn(today);
        when(tomorrow.getWeatherDate()).thenReturn(today.plusDays(1));

        when(weatherRepository
            .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                locationId,
                today.minusDays(1),
                today.plusDays(4)
            ))
            .thenReturn(List.of(yesterday, current, tomorrow));

        WeatherAPILocation locationDto = new WeatherAPILocation(
            latitude,
            longitude,
            latitude,
            longitude,
            List.of("서울특별시", "중구", "명동")
        );

        when(weatherLocationService.getLocation(latitude, longitude))
            .thenReturn(locationDto);

        WeatherDto todayDto = mock(WeatherDto.class);
        WeatherDto tomorrowDto = mock(WeatherDto.class);

        when(weatherDtoMapper.toDto(current, yesterday, locationDto))
            .thenReturn(todayDto);

        when(weatherDtoMapper.toDto(tomorrow, current, locationDto))
            .thenReturn(tomorrowDto);

        List<WeatherDto> result =
            weatherService.getWeathers(latitude, longitude);

        assertThat(result).containsExactly(todayDto, tomorrowDto);

        verify(weatherDtoMapper)
            .toDto(current, yesterday, locationDto);

        verify(weatherDtoMapper)
            .toDto(tomorrow, current, locationDto);
    }

    @Test
    @DisplayName("getWeathers는 전날 데이터가 없으면 null을 비교값으로 전달한다")
    void getWeathersWithoutPreviousWeather() {

        double latitude = 37.5665;
        double longitude = 126.9780;

        LocalDate today = LocalDate.of(2026, 10, 28);
        ZoneId koreaZone = ZoneId.of("Asia/Seoul");

        Clock fixedClock = Clock.fixed(
            Instant.parse("2026-10-28T09:00:00Z"),
            koreaZone
        );

        when(clock.withZone(koreaZone)).thenReturn(fixedClock);

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);

        Weather current = mock(Weather.class);
        when(current.getWeatherDate()).thenReturn(today);

        when(weatherRepository
            .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                locationId,
                today.minusDays(1),
                today.plusDays(4)
            ))
            .thenReturn(List.of(current));

        WeatherAPILocation locationDto = new WeatherAPILocation(
            latitude,
            longitude,
            latitude,
            longitude,
            List.of("서울특별시", "중구", "명동")
        );

        when(weatherLocationService.getLocation(latitude, longitude))
            .thenReturn(locationDto);

        WeatherDto todayDto = mock(WeatherDto.class);

        when(weatherDtoMapper.toDto(current, null, locationDto))
            .thenReturn(todayDto);

        List<WeatherDto> result =
            weatherService.getWeathers(latitude, longitude);

        assertThat(result).containsExactly(todayDto);

        verify(weatherDtoMapper)
            .toDto(current, null, locationDto);
    }

    @Test
    @DisplayName("getWeathers는 저장된 날씨가 없으면 빈 목록을 반환한다")
    void getWeathersReturnsEmptyList() {

        double latitude = 37.5665;
        double longitude = 126.9780;

        LocalDate today = LocalDate.of(2026, 10, 28);
        ZoneId koreaZone = ZoneId.of("Asia/Seoul");

        Clock fixedClock = Clock.fixed(
            Instant.parse("2026-10-28T09:00:00Z"),
            koreaZone
        );

        when(clock.withZone(koreaZone)).thenReturn(fixedClock);

        when(weatherLocationService.findOrCreate(latitude, longitude))
            .thenReturn(location);

        when(weatherRepository
            .findByLocationIdAndWeatherDateBetweenOrderByWeatherDateAsc(
                locationId,
                today.minusDays(1),
                today.plusDays(4)
            ))
            .thenReturn(List.of());

        List<WeatherDto> result =
            weatherService.getWeathers(latitude, longitude);

        assertThat(result).isEmpty();

        verifyNoInteractions(weatherDtoMapper);

        verify(weatherLocationService, never())
            .getLocation(latitude, longitude);
    }

    private void givenForecasts(WeatherForecast... forecasts) {
        when(weatherForecastRepository.findForecastsForPeriod(
            locationId, START, END
        )).thenReturn(List.of(forecasts));
    }

    private WeatherForecast forecast(
        String forecastAt,
        double temperature,
        short humidity,
        int weatherCode,
        double precipitationProbability,
        double rainAmount,
        double snowAmount,
        double windSpeed
    ) {
        return new WeatherForecast(
            locationId,
            Instant.parse(forecastAt),
            Instant.parse("2026-10-25T12:00:00Z"),
            temperature,
            humidity,
            weatherCode,
            precipitationProbability,
            rainAmount,
            snowAmount,
            windSpeed
        );
    }

    private Weather captureSavedWeather() {
        ArgumentCaptor<Weather> captor =
            ArgumentCaptor.forClass(Weather.class);

        verify(weatherRepository).save(captor.capture());

        return captor.getValue();
    }
}
