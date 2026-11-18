package codeit.ootd.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record CommentDto(
        @Schema(description = "댓글 ID")
        UUID id,

        @Schema(description = "댓글 생성 시간")
        Instant createdAt,

        @Schema(description = "피드 ID")
        UUID feedId,

        @Schema(description = "작성자 정보")
        AuthorDto author,

        @Schema(description = "댓글 내용")
        String content
) {
}