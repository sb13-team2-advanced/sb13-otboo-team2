package codeit.ootd.infra.weather;

import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenWeatherMapClient {

    private final RestClient restClient;
    private final String apiKey;

    public OpenWeatherMapClient(
        @Qualifier("openWeatherMapRestClient") RestClient restClient,
        @Value("${ootd.weather.openweathermap.api-key}") String apiKey
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public OwmForecastResponse getForecast(double latitude, double longitude) {

        OwmForecastResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/data/2.5/forecast")
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .build())
            .retrieve()
            .body(OwmForecastResponse.class);

        if (response == null
            || response.list() == null
            || response.list().isEmpty()) {
            throw new IllegalStateException("OWM 예보 데이터가 없습니다.");
        }

        return response;
    }
}
