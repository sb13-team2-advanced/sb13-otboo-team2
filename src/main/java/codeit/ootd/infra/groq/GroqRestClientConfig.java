package codeit.ootd.infra.groq;

import codeit.ootd.global.config.OutboundHttpClientSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class GroqRestClientConfig {

    /** API별 연결 풀을 독립 생성하고 기본 크기를 유지하며 변경 가능한 설정으로 노출한다. */
    @Bean(destroyMethod = "close")
    public PoolingHttpClientConnectionManager groqConnectionManager(
        @Value("${ootd.groq.pool.max-total:25}") int maximum,
        @Value("${ootd.groq.pool.max-per-route:5}") int perRoute,
        @Value("${ootd.groq.connect-timeout:2s}") Duration connectTimeout,
        @Value("${ootd.groq.read-timeout:8s}") Duration readTimeout,
        MeterRegistry registry) {
        return OutboundHttpClientSupport.createManager("groq", maximum, perRoute,
            connectTimeout, readTimeout, registry);
    }

    /** 연결 획득 대기는 기존 Apache 기본값인 3분으로 명시하고 풀과 생명주기를 분리한다. */
    @Bean(destroyMethod = "close")
    public CloseableHttpClient groqHttpClient(
        @Qualifier("groqConnectionManager") PoolingHttpClientConnectionManager manager,
        @Value("${ootd.groq.pool.acquire-timeout:3m}") Duration acquireTimeout,
        @Value("${ootd.groq.read-timeout:8s}") Duration readTimeout) {
        return OutboundHttpClientSupport.createClient(manager, acquireTimeout, readTimeout);
    }

    /** 명시적으로 생성한 전용 HTTP 클라이언트를 사용하며 기존 RestClient 관측 설정을 유지한다. */
    @Bean
    public RestClient groqRestClient(RestClient.Builder builder,
        @Qualifier("groqHttpClient") CloseableHttpClient client,
        @Value("${ootd.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl) {
        return builder.baseUrl(baseUrl).requestFactory(new HttpComponentsClientHttpRequestFactory(client)).build();
    }
}
