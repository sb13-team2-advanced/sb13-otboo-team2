package codeit.ootd.infra.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import codeit.ootd.infra.weather.dto.KakaoRegionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("카카오 위치 API 클라이언트 테스트")
class KakaoLocalClientTest {

    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;

    private static final String EXPECTED_URL =
        "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json"
            + "?x=126.978&y=37.5665";

    private MockRestServiceServer server;
    private KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoLocalClient(builder, "test-key");
    }

    @Test
    @DisplayName("행정동 정보를 정상적으로 조회한다")
    void getAdministrativeRegionSuccess() {

        String json = """
            {
              "documents": [
                {
                  "region_type": "B",
                  "address_name": "서울특별시 중구 태평로1가",
                  "region_1depth_name": "서울특별시",
                  "region_2depth_name": "중구",
                  "region_3depth_name": "태평로1가"
                },
                {
                  "region_type": "H",
                  "address_name": "서울특별시 중구 명동",
                  "region_1depth_name": "서울특별시",
                  "region_2depth_name": "중구",
                  "region_3depth_name": "명동"
                }
              ]
            }
            """;

        server.expect(requestTo(EXPECTED_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "KakaoAK test-key"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        KakaoRegionResponse.Region region =
            client.getAdministrativeRegion(LATITUDE, LONGITUDE);

        assertThat(region.regionType()).isEqualTo("H");
        assertThat(region.region1DepthName()).isEqualTo("서울특별시");
        assertThat(region.region2DepthName()).isEqualTo("중구");
        assertThat(region.region3DepthName()).isEqualTo("명동");

        server.verify();
    }

    @Test
    @DisplayName("행정동 정보가 없으면 예외가 발생한다")
    void getAdministrativeRegionNotFound() {

        String json = """
            {
              "documents": [
                {
                  "region_type": "B",
                  "address_name": "서울특별시 중구 태평로1가"
                }
              ]
            }
            """;

        server.expect(requestTo(EXPECTED_URL))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
            client.getAdministrativeRegion(LATITUDE, LONGITUDE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("행정동 정보를 찾을 수 없습니다.");

        server.verify();
    }
}
