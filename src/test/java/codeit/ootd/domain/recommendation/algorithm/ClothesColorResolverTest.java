package codeit.ootd.domain.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClothesColorResolverTest {

    private final ClothesColorResolver resolver = new ClothesColorResolver();

    @ParameterizedTest
    @CsvSource({
        "검정, 블랙", "검정색, 블랙", "검은색, 블랙", "흰색, 화이트", "하양, 화이트",
        "하얀색, 화이트", "회색, 그레이", "남색, 네이비", "파랑, 블루", "파란색, 블루",
        "초록, 그린", "초록색, 그린", "녹색, 그린", "빨강, 레드", "빨간색, 레드",
        "갈색, 브라운", "분홍, 핑크", "분홍색, 핑크", "보라, 퍼플", "보라색, 퍼플",
        "주황, 오렌지", "주황색, 오렌지", "노랑, 옐로", "노란색, 옐로", "옐로우, 옐로",
        "BLACK, 블랙", "White, 화이트", "GRAY, 그레이", "Grey, 그레이", "NAVY, 네이비",
        "Blue, 블루", "Green, 그린", "RED, 레드", "beige, 베이지", "brown, 브라운",
        "pink, 핑크", "purple, 퍼플", "Orange, 오렌지", "Yellow, 옐로"
    })
    void 같은_색의_한글과_영문_표현을_배색_문서의_이름으로_통일한다(String value, String expected) {
        // given
        RecommendationClothesResponse clothes = clothes(List.of(attribute("색상", value)));

        // when/then
        assertThat(resolver.resolve(clothes)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "블랙", "화이트", "아이보리", "크림", "소프트 화이트", "라이트 블루", "코발트 블루",
        "스카이 블루", "블루 그레이", "선명한 레드", "데님", "적갈색", "카멜", "차콜"
    })
    void 별도_색조를_기본색에_합치지_않고_보존한다(String value) {
        // given/when/then
        assertThat(resolver.normalize(value)).contains(value);
    }

    @Test
    void 의상_이름과_다른_속성이_아닌_실제_색상_값을_사용한다() {
        // given
        RecommendationClothesResponse clothes = clothes(List.of(
            attribute("소재", "화이트"),
            attribute("색상", "주황색"),
            attribute("두께", "두꺼움")
        ));

        // when/then
        assertThat(resolver.resolve(clothes)).contains("오렌지");
    }

    @Test
    void 속성명과_값의_양끝_공백과_색상명의_연속_공백을_정리한다() {
        // given
        RecommendationClothesResponse clothes = clothes(List.of(
            attribute(" 색상 ", "  라이트\t 블루  ")
        ));

        // when/then
        assertThat(resolver.resolve(clothes)).contains("라이트 블루");
    }

    @Test
    void 속성이_없거나_색상_속성이_없으면_판단하지_않는다() {
        // given
        RecommendationClothesResponse empty = clothes(List.of());
        RecommendationClothesResponse missing = clothes(List.of(attribute("소재", "블랙")));
        RecommendationClothesResponse absent = clothes(null);

        // when/then
        assertThat(resolver.resolve(empty)).isEmpty();
        assertThat(resolver.resolve(missing)).isEmpty();
        assertThat(resolver.resolve(absent)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "미등록", "무지개", "블랙/화이트", "빨강, 파랑", "진한 파랑"})
    void 비어_있거나_미지원_색상은_임의로_추정하지_않는다(String value) {
        // given
        RecommendationClothesResponse clothes = clothes(List.of(attribute("색상", value)));

        // when/then
        assertThat(resolver.resolve(clothes)).isEmpty();
    }

    @Test
    void 서로_다른_색상_속성이_있으면_첫_값을_임의로_선택하지_않는다() {
        // given
        RecommendationClothesResponse clothes = clothes(List.of(
            attribute("색상", "검정"), attribute("색상", "흰색")
        ));

        // when/then
        assertThat(resolver.resolve(clothes)).isEmpty();
    }

    @Test
    void 중복된_색상_속성이_동일한_색이면_하나의_색으로_판단한다() {
        // given
        RecommendationClothesResponse clothes = clothes(List.of(
            attribute("색상", "검정"), attribute("색상", "블랙")
        ));

        // when/then
        assertThat(resolver.resolve(clothes)).contains("블랙");
    }

    @Test
    void 미지원_값과_지원하는_색상이_함께_있어도_판단을_보류한다() {
        // given
        RecommendationClothesResponse clothes = clothes(List.of(
            attribute("색상", "미등록"), attribute("색상", "블랙")
        ));

        // when/then
        assertThat(resolver.resolve(clothes)).isEmpty();
    }

    private RecommendationClothesResponse clothes(List<ClothesAttributeWithDefDto> attributes) {
        return new RecommendationClothesResponse(UUID.randomUUID(), "검정 셔츠", null,
            ClothesType.TOP, attributes);
    }

    private ClothesAttributeWithDefDto attribute(String name, String value) {
        return new ClothesAttributeWithDefDto(UUID.randomUUID(), name, List.of(), value);
    }
}
