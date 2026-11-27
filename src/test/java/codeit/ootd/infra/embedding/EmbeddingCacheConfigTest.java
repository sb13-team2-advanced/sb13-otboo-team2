package codeit.ootd.infra.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmbeddingCacheConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(EmbeddingCacheConfig.class)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void 기본_설정으로_제한된_캐시와_적중_미스_지표를_등록한다() {
        // given/when
        context.run(application -> {
            var properties = application.getBean(EmbeddingCacheProperties.class);
            @SuppressWarnings("unchecked")
            var cache = (Cache<EmbeddingCacheKey, EmbeddingResponse>) application.getBean("embeddingVectorCache");
            var response = new EmbeddingResponse(EmbeddingClient.EXPECTED_MODEL, 384, Collections.nCopies(384, 0.1));
            var key = EmbeddingCacheKey.of(properties, "오렌지 코디");
            cache.get(key, missing -> response);
            cache.get(key, missing -> response);
            var registry = application.getBean(MeterRegistry.class);

            // then
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.maximumSize()).isEqualTo(512);
            assertThat(properties.ttl()).isEqualTo(Duration.ofHours(24));
            assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(512);
            assertThat(cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofHours(24));
            assertThat(registry.get("cache.gets").tags("cache", "embeddingVectors", "result", "hit")
                .functionCounter().count()).isEqualTo(1);
            assertThat(registry.get("cache.gets").tags("cache", "embeddingVectors", "result", "miss")
                .functionCounter().count()).isEqualTo(1);
        });
    }

    @Test
    void 캐시_로드_성공과_실패_횟수와_누적_시간을_노출한다() {
        context.run(application -> {
            // given
            var registry = application.getBean(MeterRegistry.class);
            var stats = application.getBean("embeddingCacheStats", StatsCounter.class);

            // when
            stats.recordLoadSuccess(Duration.ofMillis(200).toNanos());
            stats.recordLoadFailure(Duration.ofMillis(300).toNanos());

            // then
            assertThat(registry.get("cache.load").tags("cache", "embeddingVectors", "result", "success")
                .functionCounter().count()).isEqualTo(1);
            assertThat(registry.get("cache.load").tags("cache", "embeddingVectors", "result", "failure")
                .functionCounter().count()).isEqualTo(1);
            assertThat(registry.get("cache.load.duration").tag("cache", "embeddingVectors")
                .timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(500);
        });
    }

    @Test
    void 캐시_용량과_만료_및_버전_설정을_변경할_수_있다() {
        // given/when
        context.withPropertyValues("ootd.embedding.cache.enabled=false", "ootd.embedding.cache.maximum-size=2", "ootd.embedding.cache.ttl=10m",
            "ootd.embedding.cache.model-revision=weights-2", "ootd.embedding.cache.preprocessing-version=query-2")
            .run(application -> {
                Cache<?, ?> cache = application.getBean("embeddingVectorCache", Cache.class);
                var properties = application.getBean(EmbeddingCacheProperties.class);

                // then
                assertThat(properties.enabled()).isFalse();
                assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(2);
                assertThat(cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofMinutes(10));
                assertThat(properties.modelRevision()).isEqualTo("weights-2");
                assertThat(properties.preprocessingVersion()).isEqualTo("query-2");
            });
    }

    @ParameterizedTest
    @CsvSource(value = {
        "0|PT1H|v1|query-v1", "-1|PT1H|v1|query-v1", "1|PT0S|v1|query-v1",
        "1|PT-1S|v1|query-v1", "1|NULL|v1|query-v1", "1|PT1H|NULL|query-v1",
        "1|PT1H|''|query-v1", "1|PT1H|v1|NULL", "1|PT1H|v1|''"
    }, delimiter = '|', nullValues = "NULL")
    void 잘못된_크기와_만료_및_버전을_거부한다(long maximum, String ttl, String revision, String preprocessing) {
        // given/when/then
        assertThatThrownBy(() -> new EmbeddingCacheProperties(true, maximum, ttl == null ? null : Duration.parse(ttl),
            revision, preprocessing)).isInstanceOf(IllegalArgumentException.class);
    }
}
