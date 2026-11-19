package codeit.ootd.domain.feed.dto.response;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "의상 정보")
public record OotdDto(
        @Schema(description = "의상 ID")
        UUID clothesId,

        @Schema(description = "의상 이름")
        String name,

        @Schema(description = "의상 이미지 URL")
        String imageUrl,

        @Schema(
                description = "의상 타입",
                allowableValues = {
                        "TOP", "BOTTOM", "DRESS", "OUTER", "UNDERWEAR",
                        "ACCESSORY", "SHOES", "SOCKS", "HAT", "BAG", "SCARF", "ETC"
                }
        )
        String type,

        @Schema(description = "의상 속성")
        List<ClothesAttributeWithDefDto> attributes
) {
}