package codeit.ootd.domain.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.enums.ClothesType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClothesTypeResolverTest {

    private final ClothesTypeResolver resolver = new ClothesTypeResolver();

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "오버핏 블랙 자켓|OUTER",
            "베이직 반팔 티셔츠|TOP",
            "와이드 청바지|BOTTOM",
            "플라워 롱 원피스|DRESS",
            "에어 러닝화|SHOES",
            "버킷햇 화이트|HAT",
            "미니 크로스백|BAG",
            "울 혼방 양말 3종|SOCKS",
            "캐시미어 목도리|SCARF",
            "남성 드로즈 속옷 세트|UNDERWEAR",
            "가죽 벨트|ACCESSORY",
            "Oversized Denim Jacket|OUTER",
            "Cargo Pants|BOTTOM",
            "Classic Sneakers|SHOES"
    })
    void 제목의_키워드로_종류를_정한다(String title, ClothesType expected) {
        assertThat(resolver.resolve(title)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "데님 자켓|OUTER",
            "후드 집업|OUTER",
            "후드 티셔츠|TOP"
    })
    void 여러_키워드가_섞이면_우선순위가_높은_종류를_고른다(String title, ClothesType expected) {
        assertThat(resolver.resolve(title)).isEqualTo(expected);
    }

    @Test
    void 알_수_없거나_비어_있으면_기타로_정한다() {
        assertThat(resolver.resolve("알 수 없는 상품")).isEqualTo(ClothesType.ETC);
        assertThat(resolver.resolve("  ")).isEqualTo(ClothesType.ETC);
        assertThat(resolver.resolve(null)).isEqualTo(ClothesType.ETC);
    }
}
