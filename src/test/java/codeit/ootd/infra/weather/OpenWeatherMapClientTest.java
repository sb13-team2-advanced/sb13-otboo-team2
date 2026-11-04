package codeit.ootd.infra.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("OpenWeatherMap API 클라이언트 테스트")
class OpenWeatherMapClientTest {

    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;

    private static final String EXPECTED_URL =
        "https://api.openweathermap.org/data/2.5/forecast"
            + "?lat=37.5665&lon=126.978&appid=test-key&units=metric";

    private MockRestServiceServer server;
    private OpenWeatherMapClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenWeatherMapClient(
            builder.baseUrl("https://api.openweathermap.org").build(),
            "test-key"
        );
    }

    @Test
    @DisplayName("정상 응답이면 예보 데이터를 반환한다")
    void getForecastSuccess() {

        String json = """
            {
              "list": [
                {
                  "dt": 1792800000,
                  "main": {
                    "temp": 15.5,
                    "humidity": 70
                  },
                  "weather": [
                    {"id": 500}
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

        server.expect(requestTo(EXPECTED_URL))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        OwmForecastResponse response =
            client.getForecast(LATITUDE, LONGITUDE);

        assertThat(response.list()).hasSize(1);

        OwmForecastResponse.ForecastItem item =
            response.list().get(0);

        assertThat(item.main().temp()).isEqualTo(15.5);
        assertThat(item.main().humidity()).isEqualTo((short) 70);
        assertThat(item.weather().get(0).id()).isEqualTo(500);
        assertThat(item.pop()).isEqualTo(0.8);
        assertThat(item.rain().amount()).isEqualTo(2.5);
        assertThat(item.wind().speed()).isEqualTo(3.2);

        server.verify();
    }

    @Test
    @DisplayName("응답 본문이 없으면 예외가 발생한다")
    void getForecastEmptyBody() {

        server.expect(requestTo(EXPECTED_URL))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
            client.getForecast(LATITUDE, LONGITUDE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OWM 예보 데이터가 없습니다.");

        server.verify();
    }

    @Test
    @DisplayName("예보 목록이 null이면 예외가 발생한다")
    void getForecastNullList() {

        server.expect(requestTo(EXPECTED_URL))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
            client.getForecast(LATITUDE, LONGITUDE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OWM 예보 데이터가 없습니다.");

        server.verify();
    }

    @Test
    @DisplayName("예보 목록이 비어 있으면 예외가 발생한다")
    void getForecastEmptyList() {

        server.expect(requestTo(EXPECTED_URL))
            .andRespond(withSuccess(
                "{\"list\":[]}",
                MediaType.APPLICATION_JSON
            ));

        assertThatThrownBy(() ->
            client.getForecast(LATITUDE, LONGITUDE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OWM 예보 데이터가 없습니다.");

        server.verify();
    }
}
