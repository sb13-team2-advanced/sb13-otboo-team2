package codeit.ootd.domain.clothes.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.clothes.exception.InvalidSortFieldException;
import org.junit.jupiter.api.Test;

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
}
