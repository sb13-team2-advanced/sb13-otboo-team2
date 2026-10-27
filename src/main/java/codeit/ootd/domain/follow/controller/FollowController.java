package codeit.ootd.domain.follow.controller;

import codeit.ootd.domain.follow.dto.request.FollowCreateRequest;
import codeit.ootd.domain.follow.dto.FollowDto;
import codeit.ootd.domain.follow.dto.FollowListResponse;
import codeit.ootd.domain.follow.dto.FollowSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
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

@Tag(name = "팔로우 관리", description = "팔로우 관련 API")
@RestController
@RequestMapping("/api/follows")
public class FollowController {

    // TODO: FollowService 구현 후 위임
    @Operation(summary = "팔로우 생성", operationId = "createFollow")
    @PostMapping
    public ResponseEntity<FollowDto> createFollow(@RequestBody FollowCreateRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Operation(summary = "팔로우 요약 정보 조회", operationId = "getFollowSummary")
    @GetMapping("/summary")
    public ResponseEntity<FollowSummaryDto> getFollowSummary(@RequestParam("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Operation(summary = "팔로잉 목록 조회", operationId = "getFollowings")
    @GetMapping("/followings")
    public ResponseEntity<FollowListResponse> getFollowings() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Operation(summary = "팔로워 목록 조회", operationId = "getFollowers")
    @GetMapping("/followers")
    public ResponseEntity<FollowListResponse> getFollowers() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Operation(summary = "팔로우 취소", operationId = "cancelFollow")
    @DeleteMapping("/{followId}")
    public ResponseEntity<Void> cancelFollow(@PathVariable("followId") UUID followId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
