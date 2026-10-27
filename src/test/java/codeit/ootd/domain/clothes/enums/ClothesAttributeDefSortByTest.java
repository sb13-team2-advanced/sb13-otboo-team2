package codeit.ootd.domain.clothes.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.exception.InvalidCursorException;
import codeit.ootd.domain.clothes.exception.InvalidSortFieldException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClothesAttributeDefSortByTest {

    @Test
    void 정렬_기준이_없으면_생성일순이_기본이다() {
        assertThat(ClothesAttributeDefSortBy.from(null)).isEqualTo(ClothesAttributeDefSortBy.CREATED_AT);
        assertThat(ClothesAttributeDefSortBy.from(" ")).isEqualTo(ClothesAttributeDefSortBy.CREATED_AT);
    }

    @Test
    void 정렬_기준_문자열을_해석한다() {
        assertThat(ClothesAttributeDefSortBy.from("name")).isEqualTo(ClothesAttributeDefSortBy.NAME);
        assertThat(ClothesAttributeDefSortBy.from("createdAt")).isEqualTo(ClothesAttributeDefSortBy.CREATED_AT);
    }

    @Test
    void 지원하지_않는_정렬_기준이면_예외가_발생한다() {
        assertThatThrownBy(() -> ClothesAttributeDefSortBy.from("likeCount"))
                .isInstanceOf(InvalidSortFieldException.class);
    }

    @Test
    void 이름_커서는_문자열_그대로_사용한다() {
        assertThat(ClothesAttributeDefSortBy.NAME.parseCursor("두께")).isEqualTo("두께");
    }

    @Test
    void 생성일_커서는_시각으로_해석한다() {
        Instant instant = Instant.parse("2026-10-27T01:02:03.123456Z");

        assertThat(ClothesAttributeDefSortBy.CREATED_AT.parseCursor(instant.toString())).isEqualTo(instant);
    }

    @Test
    void 생성일_커서가_시각_형식이_아니면_예외가_발생한다() {
        assertThatThrownBy(() -> ClothesAttributeDefSortBy.CREATED_AT.parseCursor("not-a-time"))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 항목에서_다음_커서_문자열을_만든다() {
        Instant createdAt = Instant.parse("2026-10-27T01:02:03.123456Z");
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("두께", List.of("얇음"));
        ReflectionTestUtils.setField(definition, "createdAt", createdAt);

        assertThat(ClothesAttributeDefSortBy.NAME.cursorOf(definition)).isEqualTo("두께");
        assertThat(ClothesAttributeDefSortBy.CREATED_AT.cursorOf(definition)).isEqualTo(createdAt.toString());
    }

    @Test
    void 생성일_커서는_만든_문자열을_다시_해석하면_같은_시각이다() {
        Instant createdAt = Instant.parse("2026-10-27T01:02:03.123456Z");
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("두께", List.of("얇음"));
        ReflectionTestUtils.setField(definition, "createdAt", createdAt);

        String cursor = ClothesAttributeDefSortBy.CREATED_AT.cursorOf(definition);

        assertThat(ClothesAttributeDefSortBy.CREATED_AT.parseCursor(cursor)).isEqualTo(createdAt);
    }
}
