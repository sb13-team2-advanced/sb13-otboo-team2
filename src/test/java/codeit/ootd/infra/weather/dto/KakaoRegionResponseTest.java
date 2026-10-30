package codeit.ootd.infra.weather.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KakaoRegionResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("카카오 API JSON 응답을 행정구역 DTO로 변환한다")
    void deserializeKakaoRegionResponse() {

        String json = """
            {
              "meta": {"total_count": 2},
              "documents": [
                {
                  "region_type": "B",
                  "address_name": "서울특별시 중구 태평로1가",
                  "region_1depth_name": "서울특별시",
                  "region_2depth_name": "중구",
                  "region_3depth_name": "태평로1가",
                  "code": "1114010300"
                },
                {
                  "region_type": "H",
                  "address_name": "서울특별시 중구 명동",
                  "region_1depth_name": "서울특별시",
                  "region_2depth_name": "중구",
                  "region_3depth_name": "명동",
                  "code": "1114055000"
                }
              ]
            }
            """;

        KakaoRegionResponse response =
            jsonMapper.readValue(json, KakaoRegionResponse.class);

        assertThat(response.documents()).hasSize(2);

        KakaoRegionResponse.Region region = response.documents().get(1);

        assertThat(region.regionType()).isEqualTo("H");
        assertThat(region.addressName()).isEqualTo("서울특별시 중구 명동");
        assertThat(region.region1DepthName()).isEqualTo("서울특별시");
        assertThat(region.region2DepthName()).isEqualTo("중구");
        assertThat(region.region3DepthName()).isEqualTo("명동");
    }
}
