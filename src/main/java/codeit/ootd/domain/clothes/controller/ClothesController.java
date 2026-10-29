package codeit.ootd.domain.clothes.controller;

import codeit.ootd.domain.clothes.dto.request.ClothesCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDtoCursorResponse;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.service.ClothesService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/clothes")
@RequiredArgsConstructor
public class ClothesController {

    private final ClothesService clothesService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClothesDto> create(
            @Valid @RequestPart("request") ClothesCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clothesService.create(request, image));
    }

    @GetMapping
    public ResponseEntity<ClothesDtoCursorResponse> list(
            @RequestParam UUID ownerId,
            @RequestParam(required = false) ClothesType typeEqual,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam int limit) {
        return ResponseEntity.ok(clothesService.list(ownerId, typeEqual, cursor, idAfter, limit));
    }

    @PatchMapping(value = "/{clothesId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClothesDto> update(
            @PathVariable UUID clothesId,
            @Valid @RequestPart("request") ClothesUpdateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(clothesService.update(clothesId, request, image));
    }

    @DeleteMapping("/{clothesId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clothesId) {
        clothesService.delete(clothesId);
        return ResponseEntity.noContent().build();
    }
}
