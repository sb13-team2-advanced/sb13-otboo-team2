package codeit.ootd.infra.web;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 서버가 대신 요청해도 되는 주소인지 검사한다. 내부망, 로컬, 메타데이터 주소로의 요청(SSRF)을 막는다.
 */
@Component
public class UrlSafetyValidator {

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

    public URI validate(String url) {
        throw new UnsupportedOperationException("not implemented");
    }
}
