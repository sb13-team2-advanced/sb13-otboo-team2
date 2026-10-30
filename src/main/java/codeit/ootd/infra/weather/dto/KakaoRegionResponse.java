package codeit.ootd.infra.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoRegionResponse(
    List<Region> documents
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Region(

        @JsonProperty("region_type")
        String regionType,

        @JsonProperty("address_name")
        String addressName,

        @JsonProperty("region_1depth_name")
        String region1DepthName,

        @JsonProperty("region_2depth_name")
        String region2DepthName,

        @JsonProperty("region_3depth_name")
        String region3DepthName
    ) {
    }
}
