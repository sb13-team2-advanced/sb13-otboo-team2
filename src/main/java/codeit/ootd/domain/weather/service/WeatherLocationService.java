package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.infra.kakao.KakaoLocalClient;
import codeit.ootd.infra.weather.H3Service;
import codeit.ootd.infra.weather.dto.KakaoRegionResponse;
import com.uber.h3core.util.LatLng;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherLocationService {

    private final H3Service h3Service;
    private final WeatherLocationRepository weatherLocationRepository;
    private final KakaoLocalClient kakaoLocalClient;

    public WeatherLocation findOrCreate(double latitude, double longitude) {

        String cellId = h3Service.toCellId(latitude, longitude);

        return weatherLocationRepository.findByCellId(cellId)
            .orElseGet(() -> {
                LatLng center = h3Service.toCellCenter(cellId);

                WeatherLocation location = new WeatherLocation(
                    cellId,
                    center.lat,
                    center.lng,
                    Instant.now()
                );

                try {
                    return weatherLocationRepository.saveAndFlush(location);
                } catch (DataIntegrityViolationException e) {
                    return weatherLocationRepository.findByCellId(cellId)
                        .orElseThrow(() -> e);
                }
            });
    }

    public WeatherAPILocation getLocation(
        double latitude,
        double longitude
    ) {
        KakaoRegionResponse.Region region = kakaoLocalClient.getAdministrativeRegion(
            latitude,
            longitude
        );

        List<String> locationNames = List.of(
            region.region1DepthName(),
            region.region2DepthName(),
            region.region3DepthName()
        );

        return new WeatherAPILocation(
            latitude,
            longitude,
            latitude,
            longitude,
            locationNames
        );
    }
}
