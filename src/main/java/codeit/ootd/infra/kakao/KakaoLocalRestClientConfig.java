package codeit.ootd.infra.kakao;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class KakaoLocalRestClientConfig {

    @Bean
    public RestClient kakaoLocalRestClient(
        RestClient.Builder builder,
        @Value("${ootd.kakao.local.connect-timeout}")Duration connectTimeout,
        @Value("${ootd.kakao.local.read-timeout}")Duration readTimeout
    ) {
        HttpClientSettings settings = HttpClientSettings.defaults()
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout);

        return builder
            .baseUrl("https://dapi.kakao.com")
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect().build(settings)
            )
            .build();
    }
}
