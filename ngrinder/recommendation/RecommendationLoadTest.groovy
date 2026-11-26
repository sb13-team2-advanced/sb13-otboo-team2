import groovy.json.JsonSlurper
import net.grinder.plugin.http.HTTPPluginControl
import net.grinder.script.GTest
import net.grinder.script.Grinder
import net.grinder.scriptengine.groovy.junit.GrinderRunner
import net.grinder.scriptengine.groovy.junit.annotation.BeforeProcess
import net.grinder.scriptengine.groovy.junit.annotation.BeforeThread
import org.junit.Test
import org.junit.runner.RunWith
import org.ngrinder.http.HTTPRequest
import org.ngrinder.http.HTTPResponse
import java.util.concurrent.atomic.AtomicLong

/**
 * 인증을 측정 구간에서 제외하고 추천 요청 한 건을 트랜잭션 한 건으로 기록한다.
 * Agent의 resources/target.properties에서 대상과 단기 테스트 토큰을 읽는다.
 */
@RunWith(GrinderRunner)
class RecommendationLoadTest {
    private static final int TIMEOUT_MS = 15000
    private static final int OK = 200
    private static final long NANOS_PER_SECOND = 1_000_000_000L
    private static final long NANOS_PER_MILLISECOND = 1_000_000L
    private static final long NANOS_PER_MICROSECOND = 1000L
    private static GTest recommendation
    private static String endpoint
    private static String token
    private static String weatherId
    private static long intervalNanos
    private static long nextRequestAt
    private static HTTPRequest request
    private static String tracePrefix
    private static final AtomicLong traceSequence = new AtomicLong()

    /** 대상·인증 정보를 검사하고 요청 제한 시간을 설정한다. */
    @BeforeProcess
    static void beforeProcess() {
        Properties settings = new Properties()

        new File('resources/target.properties').withInputStream { settings.load(it) }

        String[] scenario = System.getProperty('param', 'real_1').split('_')

        assert scenario.length >= 2 && scenario[0] in ['real', 'mock']
        assert scenario.length == 2 || (scenario.length == 3 && scenario[0] == 'mock' && scenario[2] == 'trace')

        String baseUrl = settings.getProperty(scenario[0] + 'BaseUrl',
                'http://host.docker.internal:' + (scenario[0] == 'mock' ? '8082' : '8081')).trim()

        token = settings.getProperty('accessToken', '').trim()

        weatherId = settings.getProperty('weatherId', '').trim()

        double targetRps = Double.parseDouble(scenario[1]) / 10.0

        assert targetRps > 0

        intervalNanos = (long) (NANOS_PER_SECOND / targetRps)

        assert baseUrl.startsWith('http://') || baseUrl.startsWith('https://')
        assert !token.isEmpty()

        UUID.fromString(weatherId)

        endpoint = baseUrl.replaceAll('/+$', '') + '/api/recommendations?weatherId=' + weatherId

        HTTPPluginControl.getConnectionDefaults().timeout = TIMEOUT_MS

        request = new HTTPRequest()
        if (scenario.length == 3) {
            tracePrefix = 'load-' + UUID.randomUUID()
        }

        recommendation = new GTest(1, 'GET recommendations - Groq ON')
    }

    /** 각 스레드에서 HTTP 호출을 계측하며 응답 검증이 끝날 때까지 통계 보고를 지연한다. */
    @BeforeThread
    void beforeThread() {
        recommendation.record(request)
        Grinder.grinder.statistics.delayReports = true
    }

    /** 빈 추천·출처 누락을 실패로 기록한다. Groq 성공 여부는 서버 측에서 별도로 확인한다. */
    @Test
    void 의상추천_조회() {
        pace()
        String traceId = tracePrefix == null ? null : tracePrefix + '-' + traceSequence.incrementAndGet()
        long startMs = System.currentTimeMillis()
        long startedAt = System.nanoTime()
        long headersAt = 0L
        long bodyReadAt = 0L
        long bodyReadMs = 0L
        int status = 0
        boolean valid = false

        try {
            Map headers = ['Authorization': 'Bearer ' + token]
            if (traceId != null) {
                headers['X-Request-ID'] = traceId
            }
            HTTPResponse response = request.GET(endpoint, [:], headers)
            status = response.statusCode
            if (traceId != null) {
                headersAt = System.nanoTime()
            }
            assert status == OK
            String bodyText = response.bodyText
            if (traceId != null) {
                bodyReadAt = System.nanoTime()
                bodyReadMs = System.currentTimeMillis()
            }
            Map body = (Map) new JsonSlurper().parseText(bodyText)
            assert body.weatherId == weatherId
            assert body.clothes instanceof List && !body.clothes.isEmpty()
            assert body.sources instanceof List && !body.sources.isEmpty()
            assert body.explanation instanceof String && !body.explanation.trim().isEmpty()
            valid = true
        } finally {
            long latencyMicros = (System.nanoTime() - startedAt) / NANOS_PER_MICROSECOND
            Grinder.grinder.logger.info('PERF startMs={} endMs={} status={} latencyMicros={} valid={}',
                    startMs, System.currentTimeMillis(), status, latencyMicros, valid)
            if (traceId != null) {
                long headersMicros = headersAt == 0L ? -1L : (headersAt - startedAt).intdiv(NANOS_PER_MICROSECOND)
                long httpMicros = bodyReadAt == 0L ? -1L : (bodyReadAt - startedAt).intdiv(NANOS_PER_MICROSECOND)
                Grinder.grinder.logger.info('TRACE id={} startMs={} bodyReadMs={} status={} totalUs={} headersUs={} httpUs={}',
                        traceId, startMs, bodyReadMs, status, latencyMicros, headersMicros, httpMicros)
            }
        }
    }

    /** 단일 프로세스에서 요청 시작 간격을 배분하며 지연된 요청을 한꺼번에 따라잡지 않는다. */
    private static void pace() {
        if (intervalNanos == 0L) {
            return
        }

        long scheduledAt
        synchronized (RecommendationLoadTest) {
            scheduledAt = Math.max(nextRequestAt, System.nanoTime())
            nextRequestAt = scheduledAt + intervalNanos
        }

        long remaining = scheduledAt - System.nanoTime()
        if (remaining > 0L) {
            Thread.sleep((long) (remaining / NANOS_PER_MILLISECOND), (int) (remaining % NANOS_PER_MILLISECOND))
        }
    }
}
