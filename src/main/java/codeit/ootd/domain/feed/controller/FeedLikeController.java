package codeit.ootd.domain.feed.controller;

import codeit.ootd.domain.feed.service.FeedLikeService;
import java.util.UUID;

import codeit.ootd.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "피드 관리", description = "피드 관련 API")
@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedLikeController {

    private final FeedLikeService feedLikeService;

    @Operation(summary = "피드 좋아요", description = "피드 좋아요 API", operationId = "likeFeed")
    @ApiResponse(responseCode = "204", description = "피드 좋아요 성공", content = @Content)
    @ApiResponse(
            responseCode = "400",
            description = "피드 좋아요 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @PostMapping("/{feedId}/like")
    public ResponseEntity<Void> like(
            @PathVariable UUID feedId,
            @AuthenticationPrincipal UUID userId
    ) {
        feedLikeService.like(feedId, userId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "피드 좋아요 취소", description = "피드 좋아요 취소 API", operationId = "unlikeFeed")
    @ApiResponse(responseCode = "204", description = "피드 좋아요 취소 성공", content = @Content)
    @ApiResponse(
            responseCode = "400",
            description = "피드 좋아요 취소 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @DeleteMapping("/{feedId}/like")
    public ResponseEntity<Void> unlike(
            @PathVariable UUID feedId,
            @AuthenticationPrincipal UUID userId
    ) {
        feedLikeService.unlike(feedId, userId);

        return ResponseEntity.noContent().build();
    }
}