package codeit.ootd.infra.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;

class SafeWebPageFetcherTest {

    private static final String URL = "https://shop.example/p/1";
    private static final String HTML_TYPE = "text/html; charset=UTF-8";
    private static final int MAX_BYTES = 1024 * 1024;

    private static final UrlSafetyValidator.HostResolver RESOLVER = host -> {
        if (host.equals("shop.example")) {
            return new InetAddress[] {InetAddress.getByName("93.184.216.34")};
        }
        return new InetAddress[] {InetAddress.getByName(host)};
    };

    private HttpClient client;
    private SafeWebPageFetcher fetcher;

    @BeforeEach
    void setUp() {
        client = mock(HttpClient.class);
        fetcher = new SafeWebPageFetcher(new UrlSafetyValidator(RESOLVER), client);
    }

    @Test
    void html_본문을_UTF8로_읽어_돌려준다() throws Exception {
        stub(ok("<html><title>청바지</title></html>"));

        assertThat(fetcher.fetch(URL).html()).isEqualTo("<html><title>청바지</title></html>");
    }

    @Test
    void 응답_헤더의_charset으로_본문을_해석한다() throws Exception {
        byte[] body = "<html><title>청바지</title></html>".getBytes(java.nio.charset.Charset.forName("EUC-KR"));
        stub(response(200, "text/html; charset=EUC-KR", null, body));

        assertThat(fetcher.fetch(URL).html()).isEqualTo("<html><title>청바지</title></html>");
    }

    @Test
    void 알_수_없는_charset이면_UTF8로_해석한다() throws Exception {
        byte[] body = "<html><title>청바지</title></html>".getBytes(StandardCharsets.UTF_8);
        stub(response(200, "text/html; charset=no-such-charset", null, body));

        assertThat(fetcher.fetch(URL).html()).isEqualTo("<html><title>청바지</title></html>");
    }

    @Test
    void charset이_없으면_UTF8로_해석한다() throws Exception {
        byte[] body = "<html><title>청바지</title></html>".getBytes(StandardCharsets.UTF_8);
        stub(response(200, "text/html", null, body));

        assertThat(fetcher.fetch(URL).html()).isEqualTo("<html><title>청바지</title></html>");
    }

    @Test
    void 짧은_시간제한과_일반적인_User_Agent로_쿠키없이_요청한다() throws Exception {
        stub(ok("<html></html>"));

        fetcher.fetch(URL);

        HttpRequest request = captureRequests(1).get(0);
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().toString()).isEqualTo(URL);
        assertThat(request.timeout()).isPresent();
        assertThat(request.headers().firstValue("User-Agent")).isPresent();
        assertThat(request.headers().firstValue("User-Agent").orElse("")).doesNotContain("@");
        assertThat(request.headers().firstValue("Cookie")).isEmpty();
        assertThat(request.headers().firstValue("Authorization")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 500})
    void 성공이_아닌_응답은_가져오기_실패로_처리한다(int status) throws Exception {
        stub(response(status, HTML_TYPE, null, "x".getBytes(StandardCharsets.UTF_8)));

        assertFails(URL, ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED);
    }

    @Test
    void html이_아닌_응답은_가져오기_실패로_처리한다() throws Exception {
        stub(response(200, "image/png", null, new byte[] {1, 2, 3}));

        assertFails(URL, ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED);
    }

    @Test
    void 리다이렉트는_따라가고_다음_주소도_검사한다() throws Exception {
        stub(redirect(301, "https://shop.example/new"), ok("<html>새 페이지</html>"));

        assertThat(fetcher.fetch(URL).html()).isEqualTo("<html>새 페이지</html>");

        List<HttpRequest> requests = captureRequests(2);
        assertThat(requests.get(1).uri().toString()).isEqualTo("https://shop.example/new");
    }

    @Test
    void 리다이렉트가_없으면_요청한_주소를_최종_주소로_돌려준다() throws Exception {
        stub(ok("<html></html>"));

        assertThat(fetcher.fetch(URL).url()).isEqualTo(URL);
    }

    @Test
    void 리다이렉트를_따라가면_최종_주소를_함께_돌려준다() throws Exception {
        stub(redirect(301, "https://shop.example/new/path"), ok("<html></html>"));

        assertThat(fetcher.fetch(URL).url()).isEqualTo("https://shop.example/new/path");
    }

    @Test
    void 상대_경로_리다이렉트는_현재_주소를_기준으로_따라간다() throws Exception {
        stub(redirect(302, "/moved"), ok("<html></html>"));

        fetcher.fetch(URL);

        assertThat(captureRequests(2).get(1).uri().toString()).isEqualTo("https://shop.example/moved");
    }

    @Test
    void 내부_주소로_리다이렉트되면_따라가지_않고_거부한다() throws Exception {
        stub(redirect(302, "http://127.0.0.1/admin"));

        assertFails(URL, ErrorCode.CLOTHES_EXTRACTION_INVALID_URL);

        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void 이동할_주소가_없는_리다이렉트는_실패로_처리한다() throws Exception {
        stub(response(302, HTML_TYPE, null, new byte[0]));

        assertFails(URL, ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED);
    }

    @Test
    void 리다이렉트가_세_번을_넘으면_실패로_처리한다() throws Exception {
        stub(redirect(302, "https://shop.example/1"), redirect(302, "https://shop.example/2"),
                redirect(302, "https://shop.example/3"), redirect(302, "https://shop.example/4"),
                redirect(302, "https://shop.example/5"));

        assertFails(URL, ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED);

        verify(client, times(4)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void 본문이_1MB를_넘으면_실패로_처리한다() throws Exception {
        stub(response(200, HTML_TYPE, null, new byte[MAX_BYTES + 1]));

        assertFails(URL, ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED);
    }

    @Test
    void 정확히_1MB인_본문은_읽는다() throws Exception {
        stub(response(200, HTML_TYPE, null, new byte[MAX_BYTES]));

        assertThat(fetcher.fetch(URL).html()).hasSize(MAX_BYTES);
    }

    @Test
    void 네트워크_오류는_가져오기_실패로_처리한다() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("연결 실패"));

        assertFails(URL, ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED);
    }

    @Test
    void 대기_중_인터럽트되면_실패로_처리하고_인터럽트_상태를_되돌린다() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException());

        assertFails(URL, ErrorCode.CLOTHES_EXTRACTION_FETCH_FAILED);

        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void 처음부터_위험한_주소면_요청하지_않고_거부한다() throws Exception {
        assertFails("http://169.254.169.254/latest/meta-data", ErrorCode.CLOTHES_EXTRACTION_INVALID_URL);

        verify(client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    // ---------- 준비 ----------

    private void assertFails(String url, ErrorCode code) {
        assertThatThrownBy(() -> fetcher.fetch(url))
                .isInstanceOfSatisfying(OtbooException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<HttpRequest> captureRequests(int expectedCount) throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(expectedCount)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        return captor.getAllValues();
    }

    @SafeVarargs
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stub(HttpResponse<InputStream>... responses) throws Exception {
        OngoingStubbing<HttpResponse> stubbing =
                when((HttpResponse) client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)));
        for (HttpResponse<InputStream> response : responses) {
            stubbing = stubbing.thenReturn(response);
        }
    }

    private static HttpResponse<InputStream> ok(String body) {
        return response(200, HTML_TYPE, null, body.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponse<InputStream> redirect(int status, String location) {
        return response(status, HTML_TYPE, location, new byte[0]);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, String contentType, String location, byte[] body) {
        Map<String, List<String>> headers = new HashMap<>();
        if (contentType != null) {
            headers.put("Content-Type", List.of(contentType));
        }
        if (location != null) {
            headers.put("Location", List.of(location));
        }
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(body));
        return response;
    }
}
