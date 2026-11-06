package codeit.ootd.infra.web;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 외부 페이지를 안전하게 가져온다. 모든 주소(리다이렉트 포함)를 검사하고, 시간·크기·형식을 제한한다.
 */
@Slf4j
@Component
public class SafeWebPageFetcher implements WebPageFetcher {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String USER_AGENT = "OtbooLinkReader/1.0";
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private final UrlSafetyValidator urlSafetyValidator;
    private final HttpClient httpClient;

    @Autowired
    public SafeWebPageFetcher(UrlSafetyValidator urlSafetyValidator) {
        this(urlSafetyValidator, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(TIMEOUT)
                .build());
    }

    public SafeWebPageFetcher(UrlSafetyValidator urlSafetyValidator, HttpClient httpClient) {
        this.urlSafetyValidator = urlSafetyValidator;
        this.httpClient = httpClient;
    }

    @Override
    public FetchedPage fetch(String url) {
        URI current = urlSafetyValidator.validate(url);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpResponse<InputStream> response = send(current);
            if (!REDIRECT_STATUSES.contains(response.statusCode())) {
                return new FetchedPage(readBody(response), current.toString());
            }
            close(response.body());
            current = urlSafetyValidator.validate(resolveLocation(current, response));
        }
        throw failed("리다이렉트가 너무 많습니다.");
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw failed("요청 실패: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed("요청 중단");
        }
    }

    private String resolveLocation(URI current, HttpResponse<InputStream> response) {
        String location = response.headers().firstValue("Location")
                .orElseThrow(() -> failed("이동할 주소가 없는 리다이렉트"));
        try {
            return current.resolve(location).toString();
        } catch (IllegalArgumentException e) {
            throw failed("올바르지 않은 이동 주소");
        }
    }

    private String readBody(HttpResponse<InputStream> response) {
        int status = response.statusCode();
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String lowered = contentType.toLowerCase(Locale.ROOT);
        if (status < 200 || status >= 300 || !(lowered.contains("text/html") || lowered.contains("xhtml"))) {
            close(response.body());
            throw failed("지원하지 않는 응답: status=" + status);
        }
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw failed("응답이 너무 큽니다.");
            }
            return new String(bytes, charsetOf(lowered));
        } catch (IOException e) {
            throw failed("본문 읽기 실패: " + e.getClass().getSimpleName());
        }
    }

    private Charset charsetOf(String loweredContentType) {
        int index = loweredContentType.indexOf("charset=");
        if (index < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = loweredContentType.substring(index + "charset=".length()).split("[;\\s]")[0].replace("\"", "");
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            return StandardCharsets.UTF_8;
        }
    }

    private void close(InputStream body) {
        try {
            if (body != null) {
                body.close();
            }
        } catch (IOException e) {
            log.debug("응답 본문 닫기 실패", e);
        }
    }

    private OtbooException failed(String reason) {
        log.warn("구매 링크 페이지 가져오기 실패: {}", reason);
        return new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED);
    }
}
