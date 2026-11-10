package codeit.ootd.domain.feed.controller;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.service.FeedCommentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedCommentController {

    private final FeedCommentService feedCommentService;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}