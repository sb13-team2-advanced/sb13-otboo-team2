package codeit.ootd.domain.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClothesPageParserTest {

    private static final String PAGE_URL = "https://shop.example/p/1";

    private final ClothesPageParser parser = new ClothesPageParser();

    @Test
    void og_태그에서_제목과_이미지를_읽는다() {
        String html = "<html><head>"
                + "<meta property=\"og:title\" content=\"오버핏 블랙 자켓\">"
                + "<meta property=\"og:image\" content=\"https://cdn.shop.example/a.jpg\">"
                + "</head><body></body></html>";

        ParsedPage page = parser.parse(html, PAGE_URL);

        assertThat(page.title()).isEqualTo("오버핏 블랙 자켓");
        assertThat(page.imageUrl()).isEqualTo("https://cdn.shop.example/a.jpg");
    }

    @Test
    void og_제목이_없으면_title_태그를_쓴다() {
        String html = "<html><head><title>  와이드 청바지 - 쇼핑몰  </title></head></html>";

        assertThat(parser.parse(html, PAGE_URL).title()).isEqualTo("와이드 청바지 - 쇼핑몰");
    }

    @Test
    void og_제목이_비어_있으면_title_태그를_쓴다() {
        String html = "<html><head><meta property=\"og:title\" content=\"  \">"
                + "<title>반팔 티셔츠</title></head></html>";

        assertThat(parser.parse(html, PAGE_URL).title()).isEqualTo("반팔 티셔츠");
    }

    @Test
    void 상대_경로_이미지는_페이지_주소를_기준으로_절대_주소로_바꾼다() {
        String html = "<meta property=\"og:image\" content=\"/img/a.jpg\">";

        assertThat(parser.parse(html, PAGE_URL).imageUrl()).isEqualTo("https://shop.example/img/a.jpg");
    }

    @Test
    void 프로토콜_생략_이미지는_페이지_프로토콜을_따른다() {
        String html = "<meta property=\"og:image\" content=\"//cdn.shop.example/a.jpg\">";

        assertThat(parser.parse(html, PAGE_URL).imageUrl()).isEqualTo("https://cdn.shop.example/a.jpg");
    }

    @Test
    void http_https가_아닌_이미지_주소는_버린다() {
        assertThat(parser.parse("<meta property=\"og:image\" content=\"javascript:alert(1)\">", PAGE_URL)
                .imageUrl()).isNull();
        assertThat(parser.parse("<meta property=\"og:image\" content=\"data:image/png;base64,AAAA\">", PAGE_URL)
                .imageUrl()).isNull();
    }

    @Test
    void 읽을_정보가_없으면_모두_null이다() {
        ParsedPage page = parser.parse("<html><body>내용 없음</body></html>", PAGE_URL);

        assertThat(page.title()).isNull();
        assertThat(page.imageUrl()).isNull();
    }

    @Test
    void 빈_문서나_null이어도_예외없이_null을_돌려준다() {
        assertThat(parser.parse("", PAGE_URL)).isEqualTo(new ParsedPage(null, null));
        assertThat(parser.parse(null, PAGE_URL)).isEqualTo(new ParsedPage(null, null));
    }
}
