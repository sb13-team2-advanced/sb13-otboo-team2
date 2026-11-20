package codeit.ootd.domain.clothes.controller;

import codeit.ootd.domain.clothes.dto.request.ClothesCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDtoCursorResponse;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.service.ClothesService;
import codeit.ootd.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "의상 관리", description = "의상 관련 API")
@RestController
@RequestMapping("/api/clothes")
@RequiredArgsConstructor
public class ClothesController {

    private final ClothesService clothesService;

    @Operation(summary = "옷 등록", description = "옷 등록 API", operationId = "createClothes")
    @ApiResponse(
            responseCode = "201",
            description = "옷 등록 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ClothesDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "옷 등록 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClothesDto> create(
            @Valid @RequestPart("request") ClothesCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clothesService.create(request, image, userId));
    }

    @Operation(summary = "옷 목록 조회", description = "옷 목록 조회 API", operationId = "getClothes")
    @ApiResponse(
            responseCode = "200",
            description = "옷 목록 조회 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ClothesDtoCursorResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "옷 목록 조회 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @GetMapping
    public ResponseEntity<ClothesDtoCursorResponse> list(
            @RequestParam UUID ownerId,
            @RequestParam(required = false) ClothesType typeEqual,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam int limit) {
        return ResponseEntity.ok(clothesService.list(ownerId, typeEqual, cursor, idAfter, limit));
    }

    @Operation(summary = "옷 수정", description = "옷 수정 API", operationId = "updateClothes")
    @ApiResponse(
            responseCode = "200",
            description = "옷 수정 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ClothesDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "옷 수정 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @PatchMapping(value = "/{clothesId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClothesDto> update(
            @PathVariable UUID clothesId,
            @Valid @RequestPart("request") ClothesUpdateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(clothesService.update(clothesId, request, image, userId));
    }

    @Operation(summary = "옷 삭제", description = "옷 삭제 API", operationId = "deleteClothes")
    @ApiResponse(
            responseCode = "204",
            description = "옷 삭제 성공"
    )
    @ApiResponse(
            responseCode = "400",
            description = "옷 삭제 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @DeleteMapping("/{clothesId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID clothesId,
            @AuthenticationPrincipal UUID userId) {
        clothesService.delete(clothesId, userId);
        return ResponseEntity.noContent().build();
    }
}
