package codeit.ootd.domain.clothes.controller;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDto;
import codeit.ootd.domain.clothes.service.ClothesAttributeDefinitionService;
import codeit.ootd.global.enums.SortDirection;
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

@RestController
@RequestMapping("/api/clothes/attribute-defs")
@RequiredArgsConstructor
public class ClothesAttributeDefinitionController {

    private final ClothesAttributeDefinitionService definitionService;

    @PostMapping
    public ResponseEntity<ClothesAttributeDefDto> create(
            @Valid @RequestBody ClothesAttributeDefCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(definitionService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<ClothesAttributeDefDto>> list(
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) SortDirection sortDirection,
            @RequestParam(required = false) String keywordLike) {
        return ResponseEntity.ok(definitionService.list(sortBy, sortDirection, keywordLike));
    }

    @PatchMapping("/{definitionId}")
    public ResponseEntity<ClothesAttributeDefDto> update(
            @PathVariable UUID definitionId,
            @Valid @RequestBody ClothesAttributeDefUpdateRequest request) {
        return ResponseEntity.ok(definitionService.update(definitionId, request));
    }

    @DeleteMapping("/{definitionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID definitionId) {
        definitionService.delete(definitionId);
        return ResponseEntity.noContent().build();
    }
}
