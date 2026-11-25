package codeit.ootd.global.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;

/** 외부 API별 연결 풀을 생성하고 사용·유휴·대기 연결을 고정 태그로 구분한다. */
public final class OutboundHttpClientSupport {

    private OutboundHttpClientSupport() {
    }

    /** 풀 크기를 검증하고 연결·응답 제한 시간과 API별 풀 지표를 설정한다. */
    public static PoolingHttpClientConnectionManager createManager(String name, int maximum, int perRoute,
        Duration connectTimeout, Duration readTimeout, MeterRegistry registry) {
        if (maximum < 1 || perRoute < 1 || perRoute > maximum) {
            throw new IllegalArgumentException("HTTP 연결 풀 크기를 확인해 주세요.");
        }
        PoolingHttpClientConnectionManager manager = PoolingHttpClientConnectionManagerBuilder.create()
            .useSystemProperties()
            .setMaxConnTotal(maximum)
            .setMaxConnPerRoute(perRoute)
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                .setSocketTimeout(Timeout.ofMilliseconds(readTimeout.toMillis())).build())
            .build();
        Gauge.builder("outbound.http.pool.leased", manager, pool -> pool.getTotalStats().getLeased())
            .tag("client", name).register(registry);
        Gauge.builder("outbound.http.pool.available", manager, pool -> pool.getTotalStats().getAvailable())
            .tag("client", name).register(registry);
        Gauge.builder("outbound.http.pool.pending", manager, pool -> pool.getTotalStats().getPending())
            .tag("client", name).register(registry);
        Gauge.builder("outbound.http.pool.max", manager, PoolingHttpClientConnectionManager::getMaxTotal)
            .tag("client", name).register(registry);
        Gauge.builder("outbound.http.pool.per.route.max", manager,
                PoolingHttpClientConnectionManager::getDefaultMaxPerRoute)
            .tag("client", name).register(registry);
        return manager;
    }

    /** Spring이 관리하는 풀을 공유하고 연결 획득 대기와 응답 제한 시간을 명시한다. */
    public static CloseableHttpClient createClient(PoolingHttpClientConnectionManager manager,
        Duration acquireTimeout, Duration readTimeout) {
        return HttpClients.custom().useSystemProperties()
            .setConnectionManager(manager).setConnectionManagerShared(true)
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(acquireTimeout.toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis())).build())
            .build();
    }
}
