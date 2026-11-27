package codeit.ootd.infra.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.ConcurrentStatsCounter;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    private static final EmbeddingResponse RESPONSE = new EmbeddingResponse(
        EmbeddingClient.EXPECTED_MODEL, 384, Collections.nCopies(384, 0.1));
    private static final EmbeddingCacheProperties PROPERTIES = new EmbeddingCacheProperties(
        true, 512, Duration.ofHours(24), "v1", "query-normalized-v1");

    @Mock
    private EmbeddingClient client;

    private final AtomicLong ticker = new AtomicLong();
    private final StatsCounter stats = new ConcurrentStatsCounter();
    private final Cache<EmbeddingCacheKey, EmbeddingResponse> cache = Caffeine.newBuilder()
        .maximumSize(PROPERTIES.maximumSize()).expireAfterWrite(PROPERTIES.ttl())
        .ticker(ticker::get).recordStats(() -> stats).build();

    @Test
    void 캐시를_끄면_같은_문장도_매번_HTTP로_조회한다() {
        // given
        var disabled = new EmbeddingCacheProperties(false, 512, Duration.ofHours(24), "v1", "query-normalized-v1");
        var service = service(disabled);
        given(client.embed("오렌지 코디")).willReturn(RESPONSE);

        // when
        service.embed("  오렌지 코디  ");
        service.embed("오렌지 코디");

        // then
        verify(client, times(2)).embed("오렌지 코디");
        assertThat(cache.estimatedSize()).isZero();
        assertThat(cache.stats().requestCount()).isZero();
    }

    @Test
    void 같은_문장의_앞뒤_공백을_정리해_벡터를_재사용한다() {
        // given
        var service = service(PROPERTIES);
        given(client.embed("오렌지 코디")).willReturn(RESPONSE);

        // when
        var first = service.embed("  오렌지 코디  ");
        var second = service.embed("오렌지 코디");

        // then
        assertThat(second).isSameAs(first).isEqualTo(RESPONSE);
        verify(client).embed("오렌지 코디");
        assertThat(cache.stats().hitCount()).isEqualTo(1);
        assertThat(cache.stats().missCount()).isEqualTo(1);
        assertThat(cache.stats().loadSuccessCount()).isEqualTo(1);
        assertThat(cache.stats().totalLoadTime()).isPositive();
    }

    @Test
    void 다른_문장과_모델_및_전처리_버전은_별도로_계산한다() {
        // given
        given(client.embed("오렌지 코디")).willReturn(RESPONSE);
        given(client.embed("블루 코디")).willReturn(RESPONSE);
        var revised = new EmbeddingCacheProperties(true, 512, Duration.ofHours(24), "v2", "query-normalized-v1");
        var preprocessed = new EmbeddingCacheProperties(true, 512, Duration.ofHours(24), "v1", "query-normalized-v2");

        // when
        service(PROPERTIES).embed("오렌지 코디");
        service(PROPERTIES).embed("블루 코디");
        service(revised).embed("오렌지 코디");
        service(preprocessed).embed("오렌지 코디");

        // then
        verify(client, times(3)).embed("오렌지 코디");
        verify(client).embed("블루 코디");
        assertThat(cache.estimatedSize()).isEqualTo(4);
    }

    @Test
    void 만료된_벡터는_외부에서_다시_조회한다() {
        // given
        var service = service(PROPERTIES);
        given(client.embed("오렌지 코디")).willReturn(RESPONSE);
        service.embed("오렌지 코디");

        // when
        ticker.addAndGet(PROPERTIES.ttl().toNanos());
        service.embed("오렌지 코디");

        // then
        verify(client, times(2)).embed("오렌지 코디");
    }

    @Test
    void 실패는_저장하지_않고_다음_요청에서_재시도한다() {
        // given
        var service = service(PROPERTIES);
        given(client.embed("오렌지 코디"))
            .willThrow(new ResourceAccessException("timeout")).willReturn(RESPONSE);

        // when/then
        assertThatThrownBy(() -> service.embed("오렌지 코디")).isInstanceOf(ResourceAccessException.class);
        assertThat(cache.estimatedSize()).isZero();
        assertThat(service.embed("오렌지 코디")).isEqualTo(RESPONSE);
        verify(client, times(2)).embed("오렌지 코디");
    }

    @Test
    void 동일한_키로_대기한_요청은_첫_실패를_공유하고_새_요청만_재시도한다() throws Exception {
        // given
        var service = service(PROPERTIES);
        var failure = new ResourceAccessException("timeout");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        given(client.embed("오렌지 코디")).willAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            throw failure;
        }).willReturn(RESPONSE);
        var first = new FutureTask<>(() -> service.embed("오렌지 코디"));
        var waiting = new FutureTask<>(() -> service.embed("오렌지 코디"));
        var firstThread = new Thread(first);
        var waitingThread = new Thread(waiting);

        try {
            // when
            firstThread.start();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            waitingThread.start();
            await().atMost(Duration.ofSeconds(5)).until(() ->
                waitingThread.getState() == Thread.State.WAITING || waitingThread.getState() == Thread.State.BLOCKED);
            release.countDown();

            // then
            assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS)).hasCauseReference(failure);
            assertThatThrownBy(() -> waiting.get(5, TimeUnit.SECONDS)).hasCauseReference(failure);
            verify(client).embed("오렌지 코디");
            assertThat(cache.estimatedSize()).isZero();
            assertThat(cache.stats().missCount()).isEqualTo(1);
            assertThat(cache.stats().loadFailureCount()).isEqualTo(1);

            assertThat(service.embed("오렌지 코디")).isEqualTo(RESPONSE);
            verify(client, times(2)).embed("오렌지 코디");
        } finally {
            release.countDown();
            firstThread.join(TimeUnit.SECONDS.toMillis(5));
            waitingThread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    @Test
    void HTTP_응답이_검증에_실패하면_저장하지_않고_정상_응답만_재사용한다() {
        // given
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new EmbeddingService(new EmbeddingClient(builder.baseUrl("http://embedding.test").build()),
            cache, stats, PROPERTIES);
        server.expect(requestTo("http://embedding.test/embeddings"))
            .andRespond(withSuccess("""
                {"model":"wrong-model","dimensions":384,"embedding":%s}
                """.formatted(RESPONSE.embedding()), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://embedding.test/embeddings"))
            .andRespond(withSuccess("""
                {"model":"intfloat/multilingual-e5-small","dimensions":384,"embedding":%s}
                """.formatted(RESPONSE.embedding()), MediaType.APPLICATION_JSON));

        // when/then
        assertThatThrownBy(() -> service.embed("오렌지 코디")).isInstanceOf(IllegalStateException.class);
        assertThat(cache.estimatedSize()).isZero();
        assertThat(service.embed("오렌지 코디")).isEqualTo(RESPONSE);
        assertThat(service.embed("오렌지 코디")).isEqualTo(RESPONSE);
        server.verify();
    }

    @Test
    void 공유_벡터를_외부에서_변경할_수_없다() {
        // given
        var values = new ArrayList<>(RESPONSE.embedding());
        given(client.embed("오렌지 코디")).willReturn(new EmbeddingResponse(RESPONSE.model(), 384, values));
        var service = service(PROPERTIES);

        // when
        var result = service.embed("오렌지 코디");
        values.set(0, 99.0);

        // then
        assertThatThrownBy(() -> result.embedding().set(0, 88.0))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(service.embed("오렌지 코디").embedding().get(0)).isEqualTo(0.1);
    }

    @Test
    void 같은_문장은_한_번만_추론하고_캐시_잠금을_공유하던_다른_문장은_기다리지_않는다() throws Exception {
        // given
        String blackTop = "블랙 상의를 기준으로 상의, 하의, 아우터와 어울리는 색 조합";
        String whiteTop = "화이트 상의를 기준으로 상의, 하의, 아우터와 어울리는 색 조합";
        var service = service(PROPERTIES);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        given(client.embed(blackTop)).willAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return RESPONSE;
        });
        given(client.embed(whiteTop)).willReturn(RESPONSE);
        ExecutorService executor = Executors.newFixedThreadPool(17);
        List<Future<EmbeddingResponse>> requests = new ArrayList<>();
        try {
            // when
            requests.add(executor.submit(() -> service.embed(blackTop)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < 15; index++) {
                requests.add(executor.submit(() -> service.embed(blackTop)));
            }
            var different = executor.submit(() -> service.embed(whiteTop));

            // then
            assertThat(different.get(5, TimeUnit.SECONDS)).isEqualTo(RESPONSE);
            assertThat(requests.get(0).isDone()).isFalse();
            release.countDown();
            for (var request : requests) {
                assertThat(request.get(5, TimeUnit.SECONDS)).isEqualTo(RESPONSE);
            }
            verify(client).embed(blackTop);
            verify(client).embed(whiteTop);
            assertThat(cache.stats().missCount()).isEqualTo(2);
            assertThat(cache.stats().loadSuccessCount()).isEqualTo(2);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 빈_문장은_캐시와_HTTP를_사용하지_않는다(String text) {
        // given/when/then
        assertThatThrownBy(() -> service(PROPERTIES).embed(text)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
        assertThat(cache.estimatedSize()).isZero();
    }

    private EmbeddingService service(EmbeddingCacheProperties properties) {
        return new EmbeddingService(client, cache, stats, properties);
    }
}
