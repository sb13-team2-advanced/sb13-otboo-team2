package codeit.ootd.domain.feed.controller;

import codeit.ootd.domain.feed.service.FeedLikeService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedLikeController {

    private final FeedLikeService feedLikeService;

    @PostMapping("/{feedId}/like")
    public ResponseEntity<Void> like(
            @PathVariable UUID feedId,
            @AuthenticationPrincipal UUID userId
    ) {
        feedLikeService.like(feedId, userId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{feedId}/like")
    public ResponseEntity<Void> unlike(
            @PathVariable UUID feedId,
            @AuthenticationPrincipal UUID userId
    ) {
        feedLikeService.unlike(feedId, userId);

        return ResponseEntity.noContent().build();
    }
}