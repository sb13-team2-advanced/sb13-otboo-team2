package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.infra.weather.H3Service;
import com.uber.h3core.util.LatLng;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeatherLocationService {

    private final H3Service h3Service;
    private final WeatherLocationRepository weatherLocationRepository;

    @Transactional
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

                return weatherLocationRepository.save(location);
            });
    }
}
