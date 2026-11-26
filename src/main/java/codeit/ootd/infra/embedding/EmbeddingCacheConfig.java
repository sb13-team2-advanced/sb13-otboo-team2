
package codeit.ootd.infra.embedding;

import codeit.ootd.global.config.CacheNames;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.ConcurrentStatsCounter;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbeddingCacheProperties.class)
public class EmbeddingCacheConfig {

    /**
     * 캐시 잠금 밖에서 실행하는 임베딩 로드 결과도 Caffeine 통계에 반영한다.
     */
    @Bean
    public StatsCounter embeddingCacheStats() {
        return new ConcurrentStatsCounter();
    }

    /**
     * 전역 CacheManager와 독립된 제한 크기의 로컬 캐시에 적중·미스·로드·퇴출 지표를 등록한다.
     */
    @Bean
    public Cache<EmbeddingCacheKey, EmbeddingResponse> embeddingVectorCache(
        EmbeddingCacheProperties properties, MeterRegistry registry,
        @Qualifier("embeddingCacheStats") StatsCounter stats) {

        Cache<EmbeddingCacheKey, EmbeddingResponse> cache = Caffeine.newBuilder()
            .maximumSize(properties.maximumSize())
            .expireAfterWrite(properties.ttl())
            .recordStats(() -> stats)
            .build();
        CaffeineCacheMetrics.monitor(registry, cache, CacheNames.EMBEDDING_VECTORS);

        FunctionCounter.builder("cache.load", stats, value -> value.snapshot().loadSuccessCount())
            .tags("cache", CacheNames.EMBEDDING_VECTORS, "result", "success")
            .description("The number of successful cache loads")
            .register(registry);

        FunctionCounter.builder("cache.load", stats, value -> value.snapshot().loadFailureCount())
            .tags("cache", CacheNames.EMBEDDING_VECTORS, "result", "failure")
            .description("The number of failed cache loads")
            .register(registry);

        TimeGauge.builder("cache.load.duration", stats, TimeUnit.NANOSECONDS, value -> value.snapshot().totalLoadTime())
            .tag("cache", CacheNames.EMBEDDING_VECTORS)
            .description("The cumulative time spent loading cache entries")
            .register(registry);

        return cache;
    }
}
