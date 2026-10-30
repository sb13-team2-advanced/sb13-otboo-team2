package codeit.ootd.domain.follow.controller;

import codeit.ootd.domain.follow.dto.request.FollowCreateRequest;
import codeit.ootd.domain.follow.dto.response.FollowDto;
import codeit.ootd.domain.follow.dto.response.FollowListResponse;
import codeit.ootd.domain.follow.dto.response.FollowSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import java.security.Principal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import codeit.ootd.domain.follow.service.FollowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    private UUID currentUserId(Principal principal) {
        return followService.currentUserId(principal == null ? null : principal.getName());
    }
    @PostMapping
    public ResponseEntity<FollowDto> createFollow(@Valid @RequestBody FollowCreateRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(followService.create(request, currentUserId(principal)));
    }

    @GetMapping("/summary")
    public ResponseEntity<FollowSummaryDto> getFollowSummary(@RequestParam("userId") UUID userId, Principal principal) {
        return ResponseEntity.ok(followService.getSummary(userId, currentUserId(principal)));
    }

    @GetMapping("/followings")
    public ResponseEntity<FollowListResponse> getFollowings(
        @RequestParam("followerId") UUID followerId,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "idAfter", required = false) UUID idAfter,
        @RequestParam("limit") int limit,
        @RequestParam(value = "nameLike", required = false) String nameLike) {
        return ResponseEntity.ok(followService.getList(followerId, true, cursor, idAfter, limit, nameLike));
    }

    @GetMapping("/followers")
    public ResponseEntity<FollowListResponse> getFollowers(
        @RequestParam("followeeId") UUID followeeId,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "idAfter", required = false) UUID idAfter,
        @RequestParam("limit") int limit,
        @RequestParam(value = "nameLike", required = false) String nameLike) {
        return ResponseEntity.ok(followService.getList(followeeId, false, cursor, idAfter, limit, nameLike));
    }

    @DeleteMapping("/{followId}")
    public ResponseEntity<Void> cancelFollow(@PathVariable("followId") UUID followId, Principal principal) {
        followService.cancel(followId, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }
}
