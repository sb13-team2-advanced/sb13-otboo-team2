package codeit.ootd.domain.clothes.extraction;

import java.net.URI;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 구매 링크 페이지의 HTML에서 상품 제목과 대표 이미지를 읽는다.
 */
@Component
public class ClothesPageParser {

    public ParsedPage parse(String html, String pageUrl) {
        if (html == null || html.isBlank()) {
            return new ParsedPage(null, null);
        }
        Document document = Jsoup.parse(html, pageUrl == null ? "" : pageUrl);
        String title = blankToNull(metaContent(document, "og:title"));
        if (title == null) {
            title = blankToNull(document.title());
        }
        return new ParsedPage(title, imageUrl(document));
    }

    private String imageUrl(Document document) {
        Element meta = document.selectFirst("meta[property=\"og:image\"]");
        if (meta == null || meta.attr("content").isBlank()) {
            return null;
        }
        String absolute = meta.absUrl("content");
        return isHttp(absolute) ? absolute : null;
    }

    private boolean isHttp(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String metaContent(Document document, String property) {
        Element meta = document.selectFirst("meta[property=\"" + property + "\"]");
        return meta == null ? null : meta.attr("content");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
