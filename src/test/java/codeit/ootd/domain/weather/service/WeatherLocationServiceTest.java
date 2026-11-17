package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import codeit.ootd.infra.kakao.KakaoLocalClient;
import codeit.ootd.infra.weather.H3Service;
import codeit.ootd.infra.weather.dto.KakaoRegionResponse;
import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class WeatherLocationServiceTest {

    @Mock
    private WeatherLocationRepository weatherLocationRepository;

    @Mock
    private H3Service h3Service;

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    @InjectMocks
    private WeatherLocationService weatherLocationService;

    @Test
    @DisplayName("새로운 H3 셀은 중심 좌표와 함께 저장한다")
    void findOrCreateSavesNewLocation() {

        double latitude = 37.5;
        double longitude = 127.0;
        String cellId = "8830e1d8d7fffff";

        when(h3Service.toCellId(latitude, longitude))
            .thenReturn(cellId);

        when(weatherLocationRepository.findByCellId(cellId))
            .thenReturn(Optional.empty());

        when(h3Service.toCellCenter(cellId))
            .thenReturn(new LatLng(37.51, 127.01));

        when(weatherLocationRepository.saveAndFlush(any(WeatherLocation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();

        WeatherLocation result =
            weatherLocationService.findOrCreate(latitude, longitude);

        Instant after = Instant.now();

        assertThat(result.getCellId()).isEqualTo(cellId);
        assertThat(result.getLatitude()).isEqualTo(37.51);
        assertThat(result.getLongitude()).isEqualTo(127.01);
        assertThat(result.getNextRefreshAt()).isBetween(before, after);

        verify(weatherLocationRepository)
            .saveAndFlush(any(WeatherLocation.class));
    }

    @Test
    @DisplayName("이미 존재하는 H3 셀은 중복 저장하지 않고 반환한다")
    void findOrCreateReturnsExistingLocation() {

        double latitude = 37.5;
        double longitude = 127.0;
        String cellId = "8830e1d8d7fffff";

        WeatherLocation existing = new WeatherLocation(
            cellId,
            37.51,
            127.01,
            Instant.now()
        );

        when(h3Service.toCellId(latitude, longitude))
            .thenReturn(cellId);

        when(weatherLocationRepository.findByCellId(cellId))
            .thenReturn(Optional.of(existing));

        WeatherLocation result =
            weatherLocationService.findOrCreate(latitude, longitude);

        assertThat(result).isSameAs(existing);

        verify(weatherLocationRepository, never())
            .saveAndFlush(any(WeatherLocation.class));
    }

    @Test
    @DisplayName("동일한 H3 셀 생성이 중복되면 기존 위치를 다시 조회한다")
    void findOrCreateReturnsExistingAfterDuplicateKey() {

        double latitude = 37.5;
        double longitude = 127.0;
        String cellId = "8830e1d8d7fffff";

        WeatherLocation existing = new WeatherLocation(
            cellId,
            37.51,
            127.01,
            Instant.now()
        );

        when(h3Service.toCellId(latitude, longitude))
            .thenReturn(cellId);

        when(weatherLocationRepository.findByCellId(cellId))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));

        when(h3Service.toCellCenter(cellId))
            .thenReturn(new LatLng(37.51, 127.01));

        when(weatherLocationRepository.saveAndFlush(any(WeatherLocation.class)))
            .thenThrow(
                new DataIntegrityViolationException("중복 셀 ID")
            );

        WeatherLocation result =
            weatherLocationService.findOrCreate(latitude, longitude);

        assertThat(result).isSameAs(existing);

        verify(weatherLocationRepository)
            .saveAndFlush(any(WeatherLocation.class));

        verify(weatherLocationRepository, org.mockito.Mockito.times(2))
            .findByCellId(cellId);
    }

    @Test
    @DisplayName("중복 저장 예외 후에도 기존 위치가 없으면 예외를 전파한다")
    void findOrCreateThrowsWhenExistingLocationNotFound() {

        double latitude = 37.5;
        double longitude = 127.0;
        String cellId = "8830e1d8d7fffff";

        when(h3Service.toCellId(latitude, longitude))
            .thenReturn(cellId);

        when(weatherLocationRepository.findByCellId(cellId))
            .thenReturn(Optional.empty());

        when(h3Service.toCellCenter(cellId))
            .thenReturn(new LatLng(37.51, 127.01));

        when(weatherLocationRepository.saveAndFlush(any(WeatherLocation.class)))
            .thenThrow(
                new DataIntegrityViolationException("DB 제약조건 위반")
            );

        assertThatThrownBy(
            () -> weatherLocationService.findOrCreate(latitude, longitude)
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessage("DB 제약조건 위반");
    }

    @Test
    @DisplayName("사용자 GPS와 카카오 행정동을 위치 응답 DTO로 변환한다")
    void getLocationReturnsWeatherAPILocation() {

        double latitude = 37.5665;
        double longitude = 126.9780;

        KakaoRegionResponse.Region region =
            new KakaoRegionResponse.Region(
                "H",
                "서울특별시 중구 명동",
                "서울특별시",
                "중구",
                "명동"
            );

        when(kakaoLocalClient.getAdministrativeRegion(latitude, longitude))
            .thenReturn(region);

        WeatherAPILocation result =
            weatherLocationService.getLocation(latitude, longitude);

        assertThat(result.latitude()).isEqualTo(latitude);
        assertThat(result.longitude()).isEqualTo(longitude);
        assertThat(result.x()).isEqualTo(latitude);
        assertThat(result.y()).isEqualTo(longitude);

        assertThat(result.locationNames())
            .containsExactly("서울특별시", "중구", "명동");

        verify(kakaoLocalClient)
            .getAdministrativeRegion(latitude, longitude);
    }

    @Test
    @DisplayName("잘못된 좌표는 카카오 API를 호출하지 않고 예외를 발생시킨다")
    void getLocationRejectsInvalidCoordinates() {
        double latitude = 100.0;
        double longitude = 127.0;

        doThrow(new OtbooException(ErrorCode.WEATHER_INVALID_COORDINATES))
            .when(h3Service)
            .validateCoordinates(latitude, longitude);

        assertThatThrownBy(
            () -> weatherLocationService.getLocation(latitude, longitude)
        )
            .isInstanceOf(OtbooException.class)
            .satisfies(exception ->
                assertThat(((OtbooException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.WEATHER_INVALID_COORDINATES)
            );

        verifyNoInteractions(kakaoLocalClient);
    }

    @Test
    @DisplayName("기존 R8 셀이 존재해도 R7 셀을 신규 생성한다")
    void findOrCreateCreatesR7LocationWhenR8Exists() throws IOException {
        double latitude = 37.5665;
        double longitude = 126.9780;

        H3Service realH3Service = new H3Service();
        H3Core h3Core = H3Core.newInstance();

        String r8CellId = h3Core.latLngToCellAddress(
            latitude,
            longitude,
            8
        );

        String r7CellId = realH3Service.toCellId(
            latitude,
            longitude
        );

        WeatherLocation existingR8 = new WeatherLocation(
            r8CellId,
            latitude,
            longitude,
            Instant.now()
        );

        when(weatherLocationRepository.findByCellId(any(String.class)))
            .thenAnswer(invocation -> {
                String cellId = invocation.getArgument(0);

                if (cellId.equals(r8CellId)) {
                    return Optional.of(existingR8);
                }

                return Optional.empty();
            });

        when(weatherLocationRepository.saveAndFlush(any(WeatherLocation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        WeatherLocationService service = new WeatherLocationService(
            realH3Service,
            weatherLocationRepository,
            kakaoLocalClient
        );

        WeatherLocation result = service.findOrCreate(
            latitude,
            longitude
        );

        assertThat(result.getCellId()).isEqualTo(r7CellId);
        assertThat(result.getCellId()).isNotEqualTo(r8CellId);
        assertThat(result.getResolution()).isEqualTo((short) 7);
        assertThat(result.getLastCollectedAt()).isNull();

        verify(weatherLocationRepository).findByCellId(r7CellId);
        verify(weatherLocationRepository, never()).findByCellId(r8CellId);
        verify(weatherLocationRepository)
            .saveAndFlush(any(WeatherLocation.class));
    }
}
