package codeit.ootd.infra.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenWeatherMapClientTest {

    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;

    private static final String EXPECTED_URL =
        "https://api.openweathermap.org/data/2.5/forecast"
            + "?lat=37.5665&lon=126.978&appid=test-key&units=metric";

    private static final String HOURLY_URL =
        "https://api.openweathermap.org/data/4.0/onecall/timeline/1h"
            + "?lat=37.5665&lon=126.978&appid=test-key&units=metric&start=";

    private static final long MIDNIGHT =
        Instant.parse("2026-11-13T15:00:00Z").getEpochSecond();

    private MockRestServiceServer server;
    private OpenWeatherMapClient client;
    private OwmOneCallUsageLimiter usageLimiter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        server = MockRestServiceServer.bindTo(builder).build();

        usageLimiter = mock(OwmOneCallUsageLimiter.class);
        when(usageLimiter.tryReserveRequest()).thenReturn(true);

        client = new OpenWeatherMapClient(
            builder.baseUrl("https://api.openweathermap.org").build(),
            "test-key",
            usageLimiter
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

    @Test
    @DisplayName("One Call 응답에서 시간별 날씨와 강수량을 읽어온다")
    void getHourlyHistorySuccess() {

        String json = """
            {
              "data": [
                {
                  "dt": %d,
                  "temp": 12.5,
                  "humidity": 75,
                  "wind_speed": 2.3,
                  "weather": [{"id": 500}],
                  "pop": 0.8,
                  "rain": {"1h": 0.5},
                  "snow": {"1h": 0.2}
                },
                {
                  "dt": %d,
                  "temp": 13.0,
                  "humidity": 70,
                  "wind_speed": 1.5,
                  "weather": [{"id": 803}]
                }
              ]
            }
            """.formatted(MIDNIGHT, MIDNIGHT + 3600);

        server.expect(requestTo(HOURLY_URL + MIDNIGHT))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        OwmOneCallHourlyResponse response =
            client.getHourlyHistory(LATITUDE, LONGITUDE, MIDNIGHT);

        assertThat(response.data()).hasSize(2);

        OwmOneCallHourlyResponse.HourlyItem first =
            response.data().get(0);

        assertThat(first.dt()).isEqualTo(MIDNIGHT);
        assertThat(first.temp()).isEqualTo(12.5);
        assertThat(first.humidity()).isEqualTo((short) 75);
        assertThat(first.windSpeed()).isEqualTo(2.3);
        assertThat(first.weather().get(0).id()).isEqualTo(500);
        assertThat(first.pop()).isEqualTo(0.8);
        assertThat(first.rain().amount()).isEqualTo(0.5);
        assertThat(first.snow().amount()).isEqualTo(0.2);

        OwmOneCallHourlyResponse.HourlyItem second =
            response.data().get(1);

        assertThat(second.pop()).isNull();
        assertThat(second.rain()).isNull();
        assertThat(second.snow()).isNull();

        server.verify();
    }

    @Test
    @DisplayName("One Call 시간별 데이터가 비어 있으면 예외가 발생한다")
    void getHourlyHistoryEmptyData() {

        server.expect(requestTo(HOURLY_URL + MIDNIGHT))
            .andRespond(withSuccess(
                "{\"data\":[]}",
                MediaType.APPLICATION_JSON
            ));

        assertThatThrownBy(() ->
            client.getHourlyHistory(LATITUDE, LONGITUDE, MIDNIGHT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OWM 과거 날씨 데이터가 없습니다.");

        server.verify();
    }

    @Test
    @DisplayName("20시간을 초과하면 다음 페이지를 조회하고 오늘 데이터만 반환한다")
    void getTodayHourlyHistoryFetchesTwoPages() {

        long secondStart = MIDNIGHT + 20 * 3600L;

        server.expect(requestTo(HOURLY_URL + MIDNIGHT))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                hourlyResponse(MIDNIGHT, 20),
                MediaType.APPLICATION_JSON
            ));

        server.expect(requestTo(HOURLY_URL + secondStart))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                hourlyResponse(secondStart, 20),
                MediaType.APPLICATION_JSON
            ));

        Instant collectedAt = Instant.parse("2026-11-14T14:30:00Z");

        List<OwmOneCallHourlyResponse.HourlyItem> result =
            client.getTodayHourlyHistory(
                LATITUDE,
                LONGITUDE,
                collectedAt
            );

        assertThat(result).hasSize(24);
        assertThat(result.get(0).dt()).isEqualTo(MIDNIGHT);
        assertThat(result.get(23).dt())
            .isEqualTo(MIDNIGHT + 23 * 3600L);

        server.verify();
    }

    @Test
    @DisplayName("자정 직후 조회하면 오늘 00시 데이터를 포함한다")
    void getTodayHourlyHistoryIncludesMidnight() {

        server.expect(requestTo(HOURLY_URL + MIDNIGHT))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                hourlyResponse(MIDNIGHT, 20),
                MediaType.APPLICATION_JSON
            ));

        Instant collectedAt = Instant.parse("2026-11-13T15:30:00Z");

        List<OwmOneCallHourlyResponse.HourlyItem> result =
            client.getTodayHourlyHistory(
                LATITUDE,
                LONGITUDE,
                collectedAt
            );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dt()).isEqualTo(MIDNIGHT);

        server.verify();
    }

    private String hourlyResponse(long firstTimestamp, int count) {

        StringBuilder json = new StringBuilder("{\"data\":[");

        for (int i = 0; i < count; i++) {
            if (i > 0) {
                json.append(",");
            }

            json.append("{\"dt\":")
                .append(firstTimestamp + i * 3600L)
                .append(",\"temp\":12.0")
                .append(",\"humidity\":70")
                .append(",\"wind_speed\":2.0")
                .append(",\"weather\":[{\"id\":803}]}");
        }

        return json.append("]}").toString();
    }

    @Test
    @DisplayName("일일 호출 제한에 도달하면 One Call API를 호출하지 않는다")
    void getHourlyHistoryRejectsExceededLimit() {
        when(usageLimiter.tryReserveRequest()).thenReturn(false);

        assertThatThrownBy(() ->
            client.getHourlyHistory(LATITUDE, LONGITUDE, MIDNIGHT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OWM One Call 일일 호출 제한(950회)에 도달했습니다.");

        server.verify();
    }
}
