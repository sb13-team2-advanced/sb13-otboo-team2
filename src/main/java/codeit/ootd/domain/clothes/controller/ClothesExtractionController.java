package codeit.ootd.domain.clothes.controller;

import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.service.ClothesExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clothes")
@RequiredArgsConstructor
public class ClothesExtractionController {

    private final ClothesExtractionService extractionService;

    @GetMapping("/extractions")
    public ResponseEntity<ClothesDto> extract(@RequestParam String url) {
        return ResponseEntity.ok(extractionService.extract(url));
    }
}
