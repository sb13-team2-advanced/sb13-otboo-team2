package codeit.ootd.infra.groq;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class GroqRestClientConfig {

    /**
     * Groq 전용 클라이언트에 연결·응답 제한 시간을 적용하며 API 키는 요청 시에만 헤더에 전달한다.
     */
    @Bean
    public RestClient groqRestClient(RestClient.Builder builder,
        @Value("${ootd.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
        @Value("${ootd.groq.connect-timeout:2s}") Duration connectTimeout,
        @Value("${ootd.groq.read-timeout:8s}") Duration readTimeout) {
        HttpClientSettings settings = HttpClientSettings.defaults()
            .withConnectTimeout(connectTimeout).withReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl)
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings)).build();
    }
}
