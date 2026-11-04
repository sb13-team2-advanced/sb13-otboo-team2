package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.infra.kakao.KakaoLocalClient;
import codeit.ootd.infra.weather.H3Service;
import codeit.ootd.infra.weather.dto.KakaoRegionResponse;
import com.uber.h3core.util.LatLng;
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
}
