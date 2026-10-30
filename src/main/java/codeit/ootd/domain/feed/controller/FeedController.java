package codeit.ootd.domain.feed.controller;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
        if (userId == null) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        if (request.authorId() == null) {
            throw new OtbooException(ErrorCode.FEED_CREATE_INVALID_REQUEST);
        }

        if (!userId.equals(request.authorId())) {
            throw new OtbooException(ErrorCode.ACCESS_DENIED);
        }

        FeedDto response = feedService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}