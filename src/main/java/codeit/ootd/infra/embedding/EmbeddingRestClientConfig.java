package codeit.ootd.infra.embedding;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class EmbeddingRestClientConfig {

    /**
     * 임베딩 서버의 주소와 연결·응답 제한 시간을 설정한다.
     */
    @Bean
    public RestClient embeddingRestClient(
            RestClient.Builder builder,
            @Value("${ootd.embedding.base-url:http://127.0.0.1:8001}")
            String baseUrl,
            @Value("${ootd.embedding.connect-timeout:2s}")
            Duration connectTimeout,
            @Value("${ootd.embedding.read-timeout:10s}")
            Duration readTimeout
    ) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(
                        ClientHttpRequestFactoryBuilder.detect().build(settings)
                )
                .build();
    }
}