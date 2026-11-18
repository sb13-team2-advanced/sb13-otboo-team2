package codeit.ootd.domain.feed.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record CommentCreateRequest(
        @Schema(description = "피드 ID")
        UUID feedId,

        @Schema(description = "작성자 ID")
        UUID authorId,

        @Schema(description = "댓글 내용")
        String content
) {
}