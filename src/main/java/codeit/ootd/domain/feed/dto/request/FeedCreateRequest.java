package codeit.ootd.domain.feed.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record FeedCreateRequest(
        @Schema(description = "작성자 ID")
        UUID authorId,

        @Schema(description = "날씨 ID")
        UUID weatherId,

        @Schema(description = "의상 ID 목록")
        List<UUID> clothesIds,

        @Schema(description = "피드 내용")
        @NotBlank(message = "피드 내용은 필수입니다.")
        @Size(max = 1000, message = "피드 내용은 1000자 이하여야 합니다.")
        String content
) {
}