package codeit.ootd.domain.recommendation.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.algorithm.ClothesColorResolver;
import codeit.ootd.domain.recommendation.dto.response.OutfitColorVariant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StyleKnowledgeMetadataParserTest {

    private static final String METADATA = """
        {
          "category": "COLOR",
          "tags": ["오렌지", "톤온톤 배색"],
          "requiredAttributes": ["색상"],
          "outfitVariants": [
            {"selectedType":"TOP", "topColor":"오렌지", "bottomColor":"적갈색",
             "outerColor":"카멜", "accessoryColor":"아이보리"},
            {"selectedType":"BOTTOM", "topColor":"적갈색", "bottomColor":"오렌지",
             "outerColor":"카멜", "accessoryColor":"아이보리"},
            {"selectedType":"OUTER", "topColor":"적갈색", "bottomColor":"카멜",
             "outerColor":"오렌지", "accessoryColor":"아이보리"}
          ]
        }
        """;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final StyleKnowledgeMetadataParser parser =
        new StyleKnowledgeMetadataParser(jsonMapper, new ClothesColorResolver());

    @Test
    void 다른_메타데이터를_무시하고_세_기준의_색_배치를_순서대로_변환한다() {
        // given/when
        List<OutfitColorVariant> result = parser.parse("orange-tonal", METADATA);

        // then
        assertThat(result).containsExactly(
            OutfitColorVariant.of(ClothesType.TOP, "오렌지", "적갈색", "카멜", "아이보리"),
            OutfitColorVariant.of(ClothesType.BOTTOM, "적갈색", "오렌지", "카멜", "아이보리"),
            OutfitColorVariant.of(ClothesType.OUTER, "적갈색", "카멜", "오렌지", "아이보리")
        );
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 색상_동의어와_공백을_정리하면서_서로_다른_색조는_보존한다() {
        // given
        String metadata = """
            {"outfitVariants":[{"selectedType":" TOP ", "topColor":" BLACK ",
              "bottomColor":"흰색", "outerColor":" 라이트  블루 ", "accessoryColor":"크림"}]}
            """;

        // when
        List<OutfitColorVariant> result = parser.parse("black-basic", metadata);

        // then
        assertThat(result).containsExactly(
            OutfitColorVariant.of(ClothesType.TOP, "블랙", "화이트", "라이트 블루", "크림"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        " ", "{", "null", "[]", "42", "{}", "{\"outfitVariants\":null}",
        "{\"outfitVariants\":{}}", "{\"outfitVariants\":\"문자열\"}", "{\"outfitVariants\":[]}"
    })
    void 메타데이터나_색_배치_배열이_없거나_잘못되면_빈_목록을_반환한다(String metadata) {
        // given/when/then
        assertThat(parser.parse("invalid", metadata)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"selectedType", "topColor", "bottomColor", "outerColor", "accessoryColor"})
    void 필수_필드가_누락된_색_배치는_제외한다(String field) {
        // given
        Map<String, Object> variant = validVariant();
        variant.remove(field);
        String metadata = jsonMapper.writeValueAsString(Map.of("outfitVariants", List.of(variant)));

        // when/then
        assertThat(parser.parse("missing-field", metadata)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRESS", "SHOES", "UNKNOWN", "top", ""})
    void 지원하지_않는_기준_의상_종류는_제외한다(String selectedType) {
        // given
        Map<String, Object> variant = validVariant();
        variant.put("selectedType", selectedType);
        String metadata = jsonMapper.writeValueAsString(Map.of("outfitVariants", List.of(variant)));

        // when/then
        assertThat(parser.parse("unsupported-type", metadata)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "true", "[]", "{}", "\"\"", "\"미등록\"", "\"블랙/화이트\""})
    void 색상이_문자열이_아니거나_지원하지_않는_값이면_제외한다(String value) {
        // given
        String metadata = """
            {"outfitVariants":[{"selectedType":"TOP", "topColor":%s,
              "bottomColor":"적갈색", "outerColor":"카멜", "accessoryColor":"아이보리"}]}
            """.formatted(value);

        // when/then
        assertThat(parser.parse("unsupported-color", metadata)).isEmpty();
    }

    @Test
    void 잘못된_항목이_섞여_있어도_유효한_색_배치는_유지한다() {
        // given
        String metadata = """
            {"outfitVariants":[null, 42, [], "문자열", {},
              {"selectedType":"TOP", "topColor":"오렌지", "bottomColor":"적갈색",
               "outerColor":"카멜", "accessoryColor":"아이보리"},
              {"selectedType":"BOTTOM", "topColor":"적갈색"}]}
            """;

        // when
        List<OutfitColorVariant> result = parser.parse("mixed", metadata);

        // then
        assertThat(result).containsExactly(
            OutfitColorVariant.of(ClothesType.TOP, "오렌지", "적갈색", "카멜", "아이보리"));
    }

    @Test
    void 실제_배색_문서_39개의_색_배치_117개를_모두_변환한다() throws IOException {
        // given
        JsonNode dataset = jsonMapper.readTree(Files.readString(
            Path.of("docs/recommendation/knowledge/style-guides.draft.json")));
        JsonNode documents = dataset.get("documents");
        assertThat(documents.size()).isEqualTo(39);

        // when/then
        for (JsonNode document : documents) {
            List<OutfitColorVariant> result = parser.parse(document.get("id").stringValue(),
                jsonMapper.writeValueAsString(document));

            assertThat(result).hasSize(3);
            assertThat(result).extracting(OutfitColorVariant::selectedType)
                .containsExactly(ClothesType.TOP, ClothesType.BOTTOM, ClothesType.OUTER);
            String baseColor = document.get("baseColor").stringValue();
            assertThat(result.get(0).topColor()).isEqualTo(baseColor);
            assertThat(result.get(1).bottomColor()).isEqualTo(baseColor);
            assertThat(result.get(2).outerColor()).isEqualTo(baseColor);
        }
    }

    private Map<String, Object> validVariant() {
        return new LinkedHashMap<>(Map.of(
            "selectedType", "TOP", "topColor", "오렌지", "bottomColor", "적갈색",
            "outerColor", "카멜", "accessoryColor", "아이보리"
        ));
    }
}
