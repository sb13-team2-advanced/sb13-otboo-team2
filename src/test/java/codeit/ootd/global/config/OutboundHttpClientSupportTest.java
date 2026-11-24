package codeit.ootd.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutboundHttpClientSupportTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void 각_풀의_점유와_대기를_독립적으로_관측한다() throws Exception {
        // given
        try (var groq = OutboundHttpClientSupport.createManager("groq", 2, 1,
                 Duration.ofSeconds(2), Duration.ofSeconds(8), registry);
             var embedding = OutboundHttpClientSupport.createManager("embedding", 3, 2,
                 Duration.ofSeconds(2), Duration.ofSeconds(10), registry)) {
            var route = new HttpRoute(new HttpHost("http", "localhost", 8099));
            ConnectionEndpoint first = groq.lease("first", route, Timeout.ofSeconds(1), null)
                .get(Timeout.ofSeconds(1));

            // when
            var waiting = groq.lease("second", route, Timeout.ofSeconds(1), null);

            // then
            assertThat(registry.get("outbound.http.pool.leased").tag("client", "groq").gauge().value()).isEqualTo(1);
            assertThat(registry.get("outbound.http.pool.pending").tag("client", "groq").gauge().value()).isEqualTo(1);
            assertThat(registry.get("outbound.http.pool.pending").tag("client", "embedding").gauge().value()).isZero();
            assertThat(registry.get("outbound.http.pool.available").tag("client", "groq").gauge().value()).isZero();
            assertThat(registry.get("outbound.http.pool.max").tag("client", "groq").gauge().value()).isEqualTo(2);
            assertThat(registry.get("outbound.http.pool.per.route.max").tag("client", "embedding").gauge().value()).isEqualTo(2);
            waiting.cancel();
            groq.release(first, null, TimeValue.ZERO_MILLISECONDS);
            assertThat(registry.get("outbound.http.pool.leased").tag("client", "groq").gauge().value()).isZero();
        }
    }

    @Test
    void 클라이언트를_닫아도_Spring이_관리하는_풀은_별도로_유지한다() throws Exception {
        // given
        try (var manager = OutboundHttpClientSupport.createManager("groq", 2, 1,
                 Duration.ofSeconds(2), Duration.ofSeconds(8), registry)) {
            var client = OutboundHttpClientSupport.createClient(manager, Duration.ofSeconds(1), Duration.ofSeconds(8));

            // when
            client.close();

            // then
            var endpoint = manager.lease("after-client-close", new HttpRoute(new HttpHost("http", "localhost", 8099)),
                Timeout.ofSeconds(1), null).get(Timeout.ofSeconds(1));
            assertThat(manager.getTotalStats().getLeased()).isEqualTo(1);
            manager.release(endpoint, null, TimeValue.ZERO_MILLISECONDS);
        }
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,0", "1,2"})
    void 잘못된_풀_크기는_거부한다(int maximum, int perRoute) {
        // given
        // when/then
        assertThatThrownBy(() -> OutboundHttpClientSupport.createManager("groq", maximum, perRoute,
                Duration.ofSeconds(2), Duration.ofSeconds(8), registry)).isInstanceOf(IllegalArgumentException.class);
    }
}
