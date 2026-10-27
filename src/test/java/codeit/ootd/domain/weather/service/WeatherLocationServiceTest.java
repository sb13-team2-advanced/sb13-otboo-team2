package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.infra.weather.H3Service;
import com.uber.h3core.util.LatLng;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherLocationServiceTest {

    @Mock
    private WeatherLocationRepository weatherLocationRepository;

    @Mock
    private H3Service h3Service;

    @InjectMocks
    private WeatherLocationService weatherLocationService;

    @Test
    @DisplayName("새로운 H3 셀은 중심 좌표와 함께 저장한다.")
    void findOrCreateSavesNewLocation () {

        double latitude = 37.5;
        double longitude = 127.0;
        String cellId = "8830e1d8d7fffff";

        when(h3Service.toCellId(latitude, longitude))
            .thenReturn(cellId);

        when(weatherLocationRepository.findByCellId(cellId))
            .thenReturn(Optional.empty());

        when(h3Service.toCellCenter(cellId))
            .thenReturn(new LatLng(37.51, 127.01));

        when(weatherLocationRepository.save(any(WeatherLocation.class)))
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
            .save(any(WeatherLocation.class));
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
            .save(any(WeatherLocation.class));
    }
}