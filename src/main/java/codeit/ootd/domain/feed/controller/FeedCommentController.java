package codeit.ootd.domain.feed.controller;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.dto.response.CommentDtoCursorResponse;
import codeit.ootd.domain.feed.service.FeedCommentService;
import java.util.UUID;

import codeit.ootd.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "피드 관리", description = "피드 관련 API")
@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedCommentController {

    private final FeedCommentService feedCommentService;

    @Operation(summary = "피드 댓글 등록", description = "피드 댓글 등록 API", operationId = "createFeedComment")
    @ApiResponse(
            responseCode = "200",
            description = "피드 댓글 등록 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = CommentDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "피드 댓글 등록 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @PostMapping(
            value = "/{feedId}/comments",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CommentDto> create(
            @PathVariable UUID feedId,
            @AuthenticationPrincipal UUID userId,
            @RequestBody CommentCreateRequest request
    ) {
        CommentDto response = feedCommentService.create(feedId, request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "피드 댓글 조회", description = "피드 댓글 조회 API", operationId = "getFeedComments")
    @ApiResponse(
            responseCode = "200",
            description = "피드 댓글 조회 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = CommentDtoCursorResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "피드 댓글 등록 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @GetMapping("/{feedId}/comments")
    public ResponseEntity<CommentDtoCursorResponse> list(
            @PathVariable UUID feedId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam int limit
    ) {
        return ResponseEntity.ok(feedCommentService.list(feedId, cursor, idAfter, limit));
    }
}