package codeit.ootd.infra.weather;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class OpenWeatherMapRestClientConfig {

    @Bean
    public RestClient openWeatherMapRestClient(
        RestClient.Builder restClientBuilder,
        @Value("${ootd.weather.openweathermap.connect-timeout}") Duration connectTimeout,
        @Value("${ootd.weather.openweathermap.read-timeout}") Duration readTimeout
    ) {

        HttpClientSettings settings = HttpClientSettings.defaults()
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout);

        return restClientBuilder
            .baseUrl("https://api.openweathermap.org")
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect().build(settings)
            )
            .build();
    }
}
