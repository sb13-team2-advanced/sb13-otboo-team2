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

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "니트 조끼|OUTER",
            "오버핏 블루종|OUTER",
            "라운드 카디건|OUTER",
            "Wool Cardigan|OUTER",
            "클래식 트렌치|OUTER",
            "경량 바람막이|OUTER",
            "양면 후리스|OUTER",
            "Light Windbreaker|OUTER",
            "쿨 반팔|TOP",
            "여름 민소매|TOP",
            "기본 나시|TOP",
            "스트라이프 폴로티|TOP",
            "카라티 블랙|TOP",
            "데일리 쇼츠|BOTTOM",
            "조거 블랙|BOTTOM",
            "카고 라이트|BOTTOM",
            "클래식 구두|SHOES",
            "플랫 슈즈|SHOES",
            "여름 뮬|SHOES",
            "워커 블랙|SHOES",
            "숄더백 블랙|BAG",
            "에코백|BAG",
            "핸드백|BAG",
            "클러치 블랙|BAG",
            "가죽 장갑|ACCESSORY",
            "실크 넥타이|ACCESSORY",
            "뿔테 안경|ACCESSORY",
            "울 넥워머|SCARF"
    })
    void 보강한_키워드로_종류를_정한다(String title, ClothesType expected) {
        assertThat(resolver.resolve(title)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "브라운 울 코트|OUTER",
            "브라운 앵클 부츠|SHOES",
            "부츠컷 데님 팬츠|BOTTOM",
            "버킷백 미니|BAG",
            "캡슐 원피스|DRESS",
            "Baggy Pants 청바지|BOTTOM",
            "Dress Shirt 화이트|TOP",
            "드레스 셔츠 화이트|TOP",
            "베스트셀러 반팔 티셔츠|TOP"
    })
    void 키워드가_다른_단어의_일부로_걸리는_경우를_바로잡는다(String title, ClothesType expected) {
        assertThat(resolver.resolve(title)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "폴로 랄프로렌 가죽 벨트|ACCESSORY",
            "폴로 랄프로렌 선글라스|ACCESSORY",
            "Polo Ralph Lauren 가죽 벨트|ACCESSORY",
            "폴로 랄프로렌 코튼 폴로티|TOP",
            "기모 플리스 팬츠|BOTTOM",
            "플리스 조거 팬츠|BOTTOM",
            "바람막이 조거 팬츠|BOTTOM",
            "후리스 바지|BOTTOM",
            "플리스 후드 집업|OUTER"
    })
    void 브랜드명과_소재_단어가_다른_종류를_덮어쓰지_않는다(String title, ClothesType expected) {
        assertThat(resolver.resolve(title)).isEqualTo(expected);
    }
}
