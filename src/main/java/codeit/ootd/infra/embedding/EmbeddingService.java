package codeit.ootd.infra.embedding;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final EmbeddingClient client;
    private final Cache<EmbeddingCacheKey, EmbeddingResponse> cache;
    private final StatsCounter stats;
    private final EmbeddingCacheProperties properties;
    private final ConcurrentMap<EmbeddingCacheKey, CompletableFuture<EmbeddingResponse>> inFlight = new ConcurrentHashMap<>();

    public EmbeddingService(EmbeddingClient client,
        @Qualifier("embeddingVectorCache") Cache<EmbeddingCacheKey, EmbeddingResponse> cache,
        @Qualifier("embeddingCacheStats") StatsCounter stats,
        EmbeddingCacheProperties properties) {
        this.client = client;
        this.cache = cache;
        this.stats = stats;
        this.properties = properties;
    }

    /**
     * HTTP 요청과 동일하게 앞뒤 공백을 제거한 문장의 검증된 벡터를 재사용한다.
     * 같은 JVM에서 동일 키로 기다리는 요청은 한 외부 호출의 결과나 예외를 공유한다.
     * 호출이 끝나면 진행 상태를 제거해 실패 이후 들어온 요청의 재시도를 허용한다.
     * 공유 벡터는 불변 복사해 호출자가 다른 요청의 결과를 변경할 수 없도록 한다.
     */
    public EmbeddingResponse embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("검색 문장이 비어 있습니다.");
        }

        if (!properties.enabled()) {
            return EmbeddingResponse.copyOf(client.embed(text.strip()));
        }

        EmbeddingCacheKey key = EmbeddingCacheKey.of(properties, text.strip());
        EmbeddingResponse cached = cache.asMap().get(key);
        if (cached != null) {
            stats.recordHits(1);
            return cached;
        }

        CompletableFuture<EmbeddingResponse> load = new CompletableFuture<>();
        CompletableFuture<EmbeddingResponse> running = inFlight.putIfAbsent(key, load);
        if (running != null) {
            return await(running);
        }

        try {
            cached = cache.getIfPresent(key);
            EmbeddingResponse response = cached != null ? cached : loadAndCache(key);
            load.complete(response);
            return response;
        } catch (RuntimeException | Error failure) {
            load.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(key, load);
        }
    }

    /** 키별 병합으로 로드 권한을 얻은 요청이 HTTP 호출을 수행하고 성공한 벡터만 캐시에 넣는다. */
    private EmbeddingResponse loadAndCache(EmbeddingCacheKey key) {
        long startedAt = System.nanoTime();

        try {
            EmbeddingResponse response = EmbeddingResponse.copyOf(client.embed(key.text()));
            cache.put(key, response);
            stats.recordLoadSuccess(System.nanoTime() - startedAt);
            return response;
        } catch (RuntimeException | Error failure) {
            stats.recordLoadFailure(System.nanoTime() - startedAt);
            throw failure;
        }
    }

    private static EmbeddingResponse await(CompletableFuture<EmbeddingResponse> load) {
        try {
            return load.join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (failure.getCause() instanceof Error cause) {
                throw cause;
            }
            throw failure;
        }
    }
}
