package codeit.ootd.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record AuthorDto(
        @Schema(description = "사용자 ID")
        UUID userId,

        @Schema(description = "사용자 이름")
        String name,

        @Schema(description = "사용자 프로필 이미지 URL")
        String profileImageUrl
) {
}