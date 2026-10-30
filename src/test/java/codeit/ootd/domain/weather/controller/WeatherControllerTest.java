package codeit.ootd.domain.weather.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.service.WeatherLocationService;
import codeit.ootd.domain.weather.service.WeatherQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class WeatherControllerTest {

    @Mock
    private WeatherLocationService weatherLocationService;

    @Mock
    private WeatherQueryService weatherQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new WeatherController(
                    weatherLocationService,
                    weatherQueryService
                )
            )
            .setMessageConverters(
                new JacksonJsonHttpMessageConverter(
                    JsonMapper.builder().build()
                )
            )
            .build();
    }

    @Test
    @DisplayName("getLocation은 좌표에 해당하는 행정동 정보를 반환한다")
    void getLocationReturnsLocationInfo() throws Exception {
        double latitude = 37.5665;
        double longitude = 126.9780;

        WeatherAPILocation response = new WeatherAPILocation(
            latitude,
            longitude,
            latitude,
            longitude,
            List.of("서울특별시", "중구", "명동")
        );

        when(weatherLocationService.getLocation(latitude, longitude))
            .thenReturn(response);

        mockMvc.perform(get("/api/weathers/location")
                .param("latitude", String.valueOf(latitude))
                .param("longitude", String.valueOf(longitude)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latitude").value(latitude))
            .andExpect(jsonPath("$.longitude").value(longitude))
            .andExpect(jsonPath("$.x").value(latitude))
            .andExpect(jsonPath("$.y").value(longitude))
            .andExpect(jsonPath("$.locationNames[0]").value("서울특별시"))
            .andExpect(jsonPath("$.locationNames[1]").value("중구"))
            .andExpect(jsonPath("$.locationNames[2]").value("명동"));

        verify(weatherLocationService)
            .getLocation(latitude, longitude);
    }

    @Test
    @DisplayName("getWeathers는 일별 날씨 목록을 200과 JSON으로 반환한다")
    void getWeathersReturnsWeatherList() throws Exception {
        double latitude = 37.5665;
        double longitude = 126.9780;
        UUID weatherId = UUID.randomUUID();

        WeatherAPILocation location = new WeatherAPILocation(
            latitude,
            longitude,
            latitude,
            longitude,
            List.of("서울특별시", "중구", "명동")
        );

        WeatherDto weather = new WeatherDto(
            weatherId,
            location,
            "CLOUDY",
            new WeatherDto.Temperature(
                19.5, 14.0, 24.0, 3.5
            ),
            new WeatherDto.Humidity(
                (short) 70, 10.0
            ),
            new WeatherDto.Precipitation(
                "RAIN", 2.5, 70.0
            ),
            new WeatherDto.WindSpeed(
                7.2, "MODERATE"
            )
        );

        when(weatherQueryService.getWeathers(latitude, longitude))
            .thenReturn(List.of(weather));

        mockMvc.perform(get("/api/weathers")
                .param("latitude", String.valueOf(latitude))
                .param("longitude", String.valueOf(longitude)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(weatherId.toString()))
            .andExpect(jsonPath("$[0].skyStatus").value("CLOUDY"))
            .andExpect(jsonPath("$[0].temperature.current").value(19.5))
            .andExpect(jsonPath("$[0].temperature.min").value(14.0))
            .andExpect(jsonPath("$[0].temperature.max").value(24.0))
            .andExpect(jsonPath("$[0].temperature.comparedToDayBefore").value(3.5))
            .andExpect(jsonPath("$[0].humidity.current").value(70))
            .andExpect(jsonPath("$[0].humidity.comparedToDayBefore").value(10.0))
            .andExpect(jsonPath("$[0].precipitation.type").value("RAIN"))
            .andExpect(jsonPath("$[0].precipitation.amount").value(2.5))
            .andExpect(jsonPath("$[0].precipitation.probability").value(70.0))
            .andExpect(jsonPath("$[0].windSpeed.speed").value(7.2))
            .andExpect(jsonPath("$[0].windSpeed.asWord").value("MODERATE"))
            .andExpect(jsonPath("$[0].location.locationNames[2]").value("명동"));

        verify(weatherQueryService)
            .getWeathers(latitude, longitude);
    }
}
