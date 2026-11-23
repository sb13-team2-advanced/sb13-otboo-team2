package codeit.ootd.domain.clothes.controller;

import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.service.ClothesExtractionRateLimiter;
import codeit.ootd.domain.clothes.service.ClothesExtractionService;
import codeit.ootd.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "의상 관리", description = "의상 관련 API")
@RestController
@RequestMapping("/api/clothes")
@RequiredArgsConstructor
public class ClothesExtractionController {

    private final ClothesExtractionService extractionService;
    private final ClothesExtractionRateLimiter rateLimiter;

    @Operation(summary = "구매 링크로 옷 정보 불러오기", description = "구매 링크로 옷 정보 불러오기 API", operationId = "extractByUrl")
    @ApiResponse(
            responseCode = "200",
            description = "구매 링크로 옷 정보 불러오기 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ClothesDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "구매 링크로 옷 정보 불러오기 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "429",
            description = "구매 링크 불러오기 요청 횟수 초과",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @GetMapping("/extractions")
    public ResponseEntity<ClothesDto> extract(@AuthenticationPrincipal UUID userId, @RequestParam String url) {
        rateLimiter.check(userId);
        return ResponseEntity.ok(extractionService.extract(url));
    }
}
