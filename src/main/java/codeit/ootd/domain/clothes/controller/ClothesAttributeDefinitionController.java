package codeit.ootd.domain.clothes.controller;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDto;
import codeit.ootd.domain.clothes.service.ClothesAttributeDefinitionService;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "의상 속성 정의", description = "의상 속성 정의 관련 API")
@RestController
@RequestMapping("/api/clothes/attribute-defs")
@RequiredArgsConstructor
public class ClothesAttributeDefinitionController {

    private final ClothesAttributeDefinitionService definitionService;

    @Operation(summary = "의상 속성 정의 등록", description = "의상 속성 정의 등록 API", operationId = "createClothesAttributeDef")
    @ApiResponse(
            responseCode = "201",
            description = "의상 속성 정의 등록 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ClothesAttributeDefDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "의상 속성 정의 등록 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @PostMapping
    public ResponseEntity<ClothesAttributeDefDto> create(
            @Valid @RequestBody ClothesAttributeDefCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(definitionService.create(request));
    }

    @Operation(summary = "의상 속성 정의 목록 조회", description = "의상 속성 정의 목록 조회 API", operationId = "getClothesAttributeDef")
    @ApiResponse(
            responseCode = "200",
            description = "의상 속성 정의 목록 조회 성공",
            content = @Content(mediaType = "*/*", array = @ArraySchema(schema = @Schema(implementation = ClothesAttributeDefDto.class)))
    )
    @ApiResponse(
            responseCode = "400",
            description = "의상 속성 정의 목록 조회 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @GetMapping
    public ResponseEntity<List<ClothesAttributeDefDto>> list(
            @Parameter(description = "정렬 기준", schema = @Schema(allowableValues = {"createdAt", "name"}))
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) SortDirection sortDirection,
            @RequestParam(required = false) String keywordLike) {
        return ResponseEntity.ok(definitionService.list(sortBy, sortDirection, keywordLike));
    }

    @Operation(summary = "의상 속성 정의 수정", description = "의상 속성 정의 수정 API", operationId = "updateClothesAttributeDef")
    @ApiResponse(
            responseCode = "200",
            description = "의상 속성 정의 수정 성공",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ClothesAttributeDefDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "의상 속성 정의 수정 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @PatchMapping("/{definitionId}")
    public ResponseEntity<ClothesAttributeDefDto> update(
            @PathVariable UUID definitionId,
            @Valid @RequestBody ClothesAttributeDefUpdateRequest request) {
        return ResponseEntity.ok(definitionService.update(definitionId, request));
    }

    @Operation(summary = "의상 속성 정의 삭제", description = "의상 속성 정의 삭제 API", operationId = "deleteClothesAttributeDef")
    @ApiResponse(
            responseCode = "204",
            description = "의상 속성 정의 삭제 성공"
    )
    @ApiResponse(
            responseCode = "400",
            description = "의상 속성 정의 삭제 실패",
            content = @Content(mediaType = "*/*", schema = @Schema(implementation = ErrorResponse.class))
    )
    @DeleteMapping("/{definitionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID definitionId) {
        definitionService.delete(definitionId);
        return ResponseEntity.noContent().build();
    }
}
