package codeit.ootd.domain.feed.controller;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.dto.request.FeedUpdateRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.FeedDtoCursorResponse;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.ErrorResponse;
import codeit.ootd.global.exception.OtbooException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "피드 관리", description = "피드 관련 API")
@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @Operation(summary = "피드 등록", description = "피드 등록 API", operationId = "createFeed")
    @ApiResponse(
            responseCode = "201",
            description = "피드 등록 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = FeedDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "피드 등록 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeedDto> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody FeedCreateRequest request,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            throw new OtbooException(ErrorCode.FEED_CREATE_INVALID_REQUEST);
        }

        FeedDto response = feedService.create(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "피드 목록 조회", description = "피드 목록 조회 API", operationId = "getFeedList")
    @ApiResponse(
            responseCode = "200",
            description = "피드 목록 조회 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = FeedDtoCursorResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "피드 목록 조회 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @GetMapping
    public ResponseEntity<FeedDtoCursorResponse> list(
            @AuthenticationPrincipal UUID userId,
            @ParameterObject @ModelAttribute FeedSearchRequest request
    ) {
        FeedDtoCursorResponse response = feedService.list(request, userId);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "피드 수정", description = "피드 수정 API", operationId = "updateFeed")
    @ApiResponse(
            responseCode = "200",
            description = "피드 수정 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = FeedDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "피드 수정 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @PatchMapping(
            value = "/{feedId}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<FeedDto> update(
            @PathVariable UUID feedId,
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody FeedUpdateRequest request,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            throw new OtbooException(ErrorCode.INVALID_REQUEST);
        }

        FeedDto response = feedService.update(feedId, request, userId);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "피드 삭제", description = "피드 삭제 API", operationId = "deleteFeed")
    @ApiResponse(responseCode = "204", description = "피드 삭제 성공", content = @Content)
    @ApiResponse(
            responseCode = "400",
            description = "피드 삭제 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID feedId,
            @AuthenticationPrincipal UUID userId
    ) {
        feedService.delete(feedId, userId);

        return ResponseEntity.noContent().build();
    }
}