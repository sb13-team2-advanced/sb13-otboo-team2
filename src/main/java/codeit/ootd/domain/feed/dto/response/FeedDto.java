package codeit.ootd.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedDto(
        @Schema(description = "피드 ID")
        UUID id,

        @Schema(description = "피드 생성 시간")
        Instant createdAt,

        @Schema(description = "피드 수정 시간")
        Instant updatedAt,

        @Schema(description = "작성자 정보")
        AuthorDto author,

        @Schema(description = "날씨 정보")
        WeatherSummaryDto weather,

        @Schema(description = "의상 정보")
        List<OotdDto> ootds,

        @Schema(description = "피드 내용")
        String content,

        @Schema(description = "좋아요 수")
        long likeCount,

        @Schema(description = "댓글 수")
        int commentCount,

        @Schema(description = "내가 좋아요를 눌렀는지 여부")
        boolean likedByMe
) {
}