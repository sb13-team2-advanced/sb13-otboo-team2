package codeit.ootd.domain.feed.controller;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.FeedDtoCursorResponse;
import codeit.ootd.domain.feed.service.FeedService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeedDto> create(
            @AuthenticationPrincipal UUID userId,
            @RequestBody FeedCreateRequest request
    ) {
        FeedDto response = feedService.create(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<FeedDtoCursorResponse> list(
            @AuthenticationPrincipal UUID userId,
            @ModelAttribute FeedSearchRequest request
    ) {
        FeedDtoCursorResponse response = feedService.list(request, userId);

        return ResponseEntity.ok(response);
    }
}