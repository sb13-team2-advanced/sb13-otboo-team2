package codeit.ootd.infra.web;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SafeWebPageFetcher implements WebPageFetcher {

    private final UrlSafetyValidator urlSafetyValidator;
    private final HttpClient httpClient;

    @Autowired
    public SafeWebPageFetcher(UrlSafetyValidator urlSafetyValidator) {
        this(urlSafetyValidator, HttpClient.newHttpClient());
    }

    public SafeWebPageFetcher(UrlSafetyValidator urlSafetyValidator, HttpClient httpClient) {
        this.urlSafetyValidator = urlSafetyValidator;
        this.httpClient = httpClient;
    }

    @Override
    public String fetch(String url) {
        throw new UnsupportedOperationException("not implemented");
    }
}
