package codeit.ootd.infra.web;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 서버가 대신 요청해도 되는 주소인지 검사한다. 내부망, 로컬, 메타데이터 주소로의 요청(SSRF)을 막는다.
 */
@Component
public class UrlSafetyValidator {

    private static final int MAX_URL_LENGTH = 2048;

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostResolver resolver;

    @Autowired
    public UrlSafetyValidator() {
        this(InetAddress::getAllByName);
    }

    public UrlSafetyValidator(HostResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * http/https, 기본 포트, 사용자 정보 없음, 풀린 주소가 모두 공개 주소인 링크만 통과시킨다.
     */
    public URI validate(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
            throw invalid();
        }
        URI uri = parse(url.strip());
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw invalid();
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || !isDefaultPort(uri.getPort())) {
            throw invalid();
        }
        for (InetAddress address : resolve(uri.getHost())) {
            if (isInternal(address)) {
                throw invalid();
            }
        }
        return uri;
    }

    private URI parse(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw invalid();
        }
    }

    private boolean isDefaultPort(int port) {
        return port == -1 || port == 80 || port == 443;
    }

    private InetAddress[] resolve(String host) {
        try {
            InetAddress[] addresses = resolver.resolve(host.replace("[", "").replace("]", ""));
            if (addresses == null || addresses.length == 0) {
                throw invalid();
            }
            return addresses;
        } catch (UnknownHostException e) {
            throw invalid();
        }
    }

    private boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 0.0.0.0/8, 100.64.0.0/10(공유 주소 공간)
            return first == 0 || (first == 100 && (second & 0xC0) == 64);
        }
        // fc00::/7 (IPv6 사설 주소)
        return (bytes[0] & 0xFE) == 0xFC;
    }

    private OtbooException invalid() {
        return new OtbooException(ErrorCode.CLOTHES_EXTRACTION_INVALID_URL);
    }
}
