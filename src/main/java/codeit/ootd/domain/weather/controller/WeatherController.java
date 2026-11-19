package codeit.ootd.domain.weather.controller;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.service.WeatherLocationService;
import codeit.ootd.domain.weather.service.WeatherQueryService;
import codeit.ootd.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "날씨 관리", description = "날씨 관련 API")
@RestController
@RequestMapping("/api/weathers")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherLocationService weatherLocationService;
    private final WeatherQueryService weatherQueryService;

    @Operation(
        summary = "날씨 위치 정보 조회",
        description = "날씨 위치 정보 조회 API",
        operationId = "getWeatherLocation"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "날씨 위치 정보 조회 성공"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "날씨 위치 정보 조회 실패",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/location")
    public ResponseEntity<WeatherAPILocation> getLocation(
        @Parameter(description = "위도")
        @RequestParam("latitude") double latitude,
        @Parameter(description = "경도")
        @RequestParam("longitude") double longitude
    ) {
        WeatherAPILocation response =
            weatherLocationService.getLocation(latitude, longitude);

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "날씨 정보 조회",
        description = "날씨 정보 조회 API",
        operationId = "getWeather"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "날씨 조회 성공"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "날씨 조회 실패",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping
    public ResponseEntity<List<WeatherDto>> getWeathers(
        @Parameter(description = "위도")
        @RequestParam("latitude") double latitude,
        @Parameter(description = "경도")
        @RequestParam("longitude") double longitude
    ) {
        List<WeatherDto> response =
            weatherQueryService.getWeathers(latitude, longitude);

        return ResponseEntity.ok(response);
    }
}
