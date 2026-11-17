package codeit.ootd.infra.weather;

import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenWeatherMapClient {

    private final RestClient restClient;
    private final String apiKey;
    private final OwmOneCallUsageLimiter usageLimiter;

    public OpenWeatherMapClient(
        @Qualifier("openWeatherMapRestClient") RestClient restClient,
        @Value("${ootd.weather.openweathermap.api-key}") String apiKey,
        OwmOneCallUsageLimiter usageLimiter
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.usageLimiter = usageLimiter;
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

    public OwmOneCallHourlyResponse getHourlyHistory(
        double latitude,
        double longitude,
        long start
    ) {
        if (!usageLimiter.tryReserveRequest()) {
            throw new IllegalStateException(
                "OWM One Call 일일 호출 제한(950회)에 도달했습니다."
            );
        }

        OwmOneCallHourlyResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/data/4.0/onecall/timeline/1h")
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .queryParam("start", start)
                .build())
            .retrieve()
            .body(OwmOneCallHourlyResponse.class);

        if (response == null
            || response.data() == null
            || response.data().isEmpty()) {
            throw new IllegalStateException("OWM 과거 날씨 데이터가 없습니다.");
        }

        return response;
    }

    public List<OwmOneCallHourlyResponse.HourlyItem> getTodayHourlyHistory(
        double latitude,
        double longitude,
        Instant collectedAt
    ) {
        ZoneId koreaZone = ZoneId.of("Asia/Seoul");

        long start = collectedAt.atZone(koreaZone)
            .toLocalDate()
            .atStartOfDay(koreaZone)
            .toEpochSecond();

        long cutoff = collectedAt.truncatedTo(ChronoUnit.HOURS)
            .getEpochSecond();

        List<OwmOneCallHourlyResponse.HourlyItem> result = new ArrayList<>();

        long nextStart = start;

        for (int page = 0; page < 2 && nextStart <= cutoff; page++) {
            List<OwmOneCallHourlyResponse.HourlyItem> items =
                getHourlyHistory(latitude, longitude, nextStart).data();

            for (OwmOneCallHourlyResponse.HourlyItem item : items) {
                if (item.dt() >= start && item.dt() <= cutoff) {
                    result.add(item);
                }
            }

            long lastTimestamp = items.get(items.size() - 1).dt();

            if (lastTimestamp >= cutoff) {
                break;
            }

            if (lastTimestamp < nextStart) {
                throw new IllegalStateException(
                    "OWM 시간별 날씨 조회 실패: 요청한 시작 시각보다 이전 데이터가 반환되었습니다."
                );
            }

            nextStart = lastTimestamp + 3600;
        }

        return result;
    }
}
