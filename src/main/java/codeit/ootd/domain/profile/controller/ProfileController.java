package codeit.ootd.domain.profile.controller;

import codeit.ootd.domain.profile.dto.request.ProfileUpdateRequest;
import codeit.ootd.domain.profile.dto.response.ProfileDto;
import codeit.ootd.domain.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/{userId}/profiles")
@RequiredArgsConstructor
@Tag(name = "프로필 관리", description = "프로필 관련 API")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "프로필 조회")
    public ResponseEntity<ProfileDto> get(@PathVariable UUID userId) {
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "프로필 업데이트")
    public ResponseEntity<ProfileDto> update(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID userId,
            @Valid @RequestPart("request") ProfileUpdateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(profileService.updateProfile(currentUserId, userId, request, image));
    }
}
