package codeit.ootd.infra.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlSafetyValidatorTest {

    /** 실제 DNS를 쓰지 않도록 호스트 이름을 고정된 주소로 풀어 준다. 이름이 아니라 IP 문자열이면 그대로 쓴다. */
    private static final UrlSafetyValidator.HostResolver RESOLVER = host -> switch (host) {
        case "shop.example" -> ips("93.184.216.34");
        case "evil.example", "localhost" -> ips("127.0.0.1");
        case "mixed.example" -> ips("93.184.216.34", "10.0.0.5");
        case "unknown.example" -> throw new UnknownHostException(host);
        default -> ips(host);
    };

    private final UrlSafetyValidator validator = new UrlSafetyValidator(RESOLVER);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://shop.example/product/1?id=2",
            "http://shop.example/p",
            "https://shop.example:443/p",
            "http://shop.example:80/p",
            "https://93.184.216.34/p"
    })
    void 공개_주소의_http_https_링크는_통과한다(String url) {
        URI uri = validator.validate(url);

        assertThat(uri).isEqualTo(URI.create(url));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "not a url",
            "ftp://shop.example/a",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "https://",
            "http://user:pw@shop.example/",
            "https://shop.example:8443/",
            "http://unknown.example/"
    })
    void 올바르지_않은_형식의_링크는_거부한다(String url) {
        assertInvalid(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/",
            "http://127.0.0.1/",
            "http://127.1.2.3/admin",
            "http://0.0.0.0/",
            "http://10.0.0.5/",
            "http://172.16.0.1/",
            "http://192.168.0.10/",
            "http://169.254.169.254/latest/meta-data",
            "http://224.0.0.1/",
            "http://[::1]/",
            "http://[fd00::1]/"
    })
    void 내부_주소로_향하는_링크는_거부한다(String url) {
        assertInvalid(url);
    }

    @Test
    void 이름이_내부_주소로_풀리면_거부한다() {
        assertInvalid("http://evil.example/");
    }

    @Test
    void 풀린_주소_중_하나라도_내부_주소면_거부한다() {
        assertInvalid("http://mixed.example/");
    }

    @Test
    void 너무_긴_주소는_거부한다() {
        assertInvalid("https://shop.example/" + "a".repeat(2100));
    }

    private void assertInvalid(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_INVALID_URL));
    }

    private static InetAddress[] ips(String... literals) throws UnknownHostException {
        InetAddress[] result = new InetAddress[literals.length];
        for (int i = 0; i < literals.length; i++) {
            result[i] = InetAddress.getByName(literals[i].replace("[", "").replace("]", ""));
        }
        return result;
    }
}
