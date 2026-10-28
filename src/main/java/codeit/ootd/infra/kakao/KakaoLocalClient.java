
package codeit.ootd.infra.kakao;

import codeit.ootd.infra.weather.dto.KakaoRegionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KakaoLocalClient {

    private final RestClient restClient;
    private final String apiKey;

    public KakaoLocalClient(
        RestClient.Builder restClientBuilder,
        @Value("${ootd.kakao.local.api-key}") String apiKey
    ) {
        this.restClient = restClientBuilder
            .baseUrl("https://dapi.kakao.com")
            .build();
        this.apiKey = apiKey;
    }

    public KakaoRegionResponse.Region getAdministrativeRegion(
        double latitude,
        double longitude
    ) {
        KakaoRegionResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/local/geo/coord2regioncode.json")
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .build())
            .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
            .retrieve()
            .body(KakaoRegionResponse.class);

        if (response == null || response.documents() == null) {
            throw new IllegalStateException("카카오 위치 응답이 없습니다.");
        }

        return response.documents().stream()
            .filter(region -> "H".equals(region.regionType()))
            .findFirst()
            .orElseThrow(() ->
                new IllegalStateException("행정동 정보를 찾을 수 없습니다.")
            );
    }
}
