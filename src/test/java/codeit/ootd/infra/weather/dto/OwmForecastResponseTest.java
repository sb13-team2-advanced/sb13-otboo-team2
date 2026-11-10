package codeit.ootd.infra.weather.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("OWM 예보 응답 변환 테스트")
class OwmForecastResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("OWM 예보 JSON에서 필요한 날씨 데이터를 읽어온다")
    void parseForecastResponse() {

        String json = """
            {
              "cod": "200",
              "cnt": 1,
              "list": [
                {
                  "dt": 1792800000,
                  "main": {
                    "temp": 15.5,
                    "humidity": 70
                  },
                  "weather": [
                    { "id": 500 }
                  ],
                  "pop": 0.8,
                  "rain": {
                    "3h": 2.5
                  },
                  "wind": {
                    "speed": 3.2
                  }
                }
              ]
            }
            """;

        OwmForecastResponse response =
            jsonMapper.readValue(json, OwmForecastResponse.class);

        OwmForecastResponse.ForecastItem forecast =
            response.list().get(0);

        assertThat(response.list()).hasSize(1);

        assertThat(forecast.dt()).isEqualTo(1792800000L);
        assertThat(forecast.main().temp()).isEqualTo(15.5);
        assertThat(forecast.main().humidity()).isEqualTo((short) 70);
        assertThat(forecast.weather().get(0).id()).isEqualTo(500);
        assertThat(forecast.pop()).isEqualTo(0.8);

        assertThat(forecast.rain().amount()).isEqualTo(2.5);

        assertThat(forecast.snow()).isNull();

        assertThat(forecast.wind().speed()).isEqualTo(3.2);
    }
}