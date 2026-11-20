package codeit.ootd.domain.recommendation.mapper;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.algorithm.ClothesColorResolver;
import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 검색 문서의 JSON 메타데이터에서 색 배치만 읽고 추천에 사용할 수 있는 객체로 변환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StyleKnowledgeMetadataParser {

    private final JsonMapper jsonMapper;
    private final ClothesColorResolver colorResolver;

    /**
     * 문서의 다른 메타데이터는 무시하고 outfitVariants 배열의 순서대로 색 배치를 반환한다.
     * 메타데이터 누락이나 잘못된 JSON은 빈 목록으로 처리하며 유효하지 않은 항목만 제외한다.
     * 기준 종류는 TOP·BOTTOM·OUTER로 제한하고 네 가지 색상은 모두 지원하는 문자열이어야 한다.
     */
    public List<OutfitColorVariant> parse(String documentKey, String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return List.of();
        }

        JsonNode metadata;
        try {
            metadata = jsonMapper.readTree(metadataJson);
        } catch (JacksonException exception) {
            log.warn("배색 문서의 메타데이터를 읽을 수 없습니다. documentKey={}", documentKey);
            return List.of();
        }

        JsonNode variants = metadata == null ? null : metadata.get("outfitVariants");
        if (variants == null || !variants.isArray()) {
            return List.of();
        }

        List<OutfitColorVariant> result = new ArrayList<>();
        for (JsonNode variant : variants) {
            parseVariant(variant).ifPresent(result::add);
        }
        if (result.size() != variants.size()) {
            log.warn("배색 문서의 유효하지 않은 색 배치를 제외했습니다. documentKey={}, excludedCount={}",
                documentKey, variants.size() - result.size());
        }
        return List.copyOf(result);
    }

    /**
     * 문자열 필드만 읽어 색상을 정규화하고 기준 종류와 필수 색상이 모두 유효할 때 생성한다.
     */
    private Optional<OutfitColorVariant> parseVariant(JsonNode variant) {
        ClothesType selectedType = switch (text(variant, "selectedType")) {
            case "TOP" -> ClothesType.TOP;
            case "BOTTOM" -> ClothesType.BOTTOM;
            case "OUTER" -> ClothesType.OUTER;
            default -> null;
        };
        String topColor = colorResolver.normalize(text(variant, "topColor")).orElse(null);
        String bottomColor = colorResolver.normalize(text(variant, "bottomColor")).orElse(null);
        String outerColor = colorResolver.normalize(text(variant, "outerColor")).orElse(null);
        String accessoryColor = colorResolver.normalize(text(variant, "accessoryColor")).orElse(null);

        if (selectedType == null || topColor == null || bottomColor == null
            || outerColor == null || accessoryColor == null) {
            return Optional.empty();
        }

        return Optional.of(OutfitColorVariant.of(selectedType, topColor, bottomColor, outerColor, accessoryColor));
    }

    /**
     * 숫자나 객체를 색상 문자열로 강제 변환하지 않고 누락 또는 다른 자료형은 빈 문자열로 반환한다.
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue().strip() : "";
    }
}
