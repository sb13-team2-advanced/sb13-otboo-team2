package codeit.ootd.domain.weather.controller;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.service.WeatherLocationService;
import codeit.ootd.domain.weather.service.WeatherQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weathers")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherLocationService weatherLocationService;
    private final WeatherQueryService weatherQueryService;

    @GetMapping("/location")
    public ResponseEntity<WeatherAPILocation> getLocation(
        @RequestParam("latitude") double latitude,
        @RequestParam("longitude") double longitude
    ) {
        WeatherAPILocation response =
            weatherLocationService.getLocation(latitude, longitude);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<WeatherDto>> getWeathers(
        @RequestParam("latitude") double latitude,
        @RequestParam("longitude") double longitude
    ) {
        List<WeatherDto> response =
            weatherQueryService.getWeathers(latitude, longitude);

        return ResponseEntity.ok(response);
    }
}
