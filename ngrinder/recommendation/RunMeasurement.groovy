import groovy.json.JsonException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.TypeChecked

import java.math.RoundingMode
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

/**
 * Controller에서 테스트를 실행하고 요청 완료 로그와 백엔드 지표를 같은 실행 결과로 저장한다.
 */
@TypeChecked
final class RunMeasurement {
    private static final URI LOCAL_CONTROLLER = URI.create('http://127.0.0.1:8300')
    private static final int REAL_PORT = 8081
    private static final int MOCK_PORT = 8082
    private static final int TEST_TIMEOUT_SECONDS = 600
    private static final int STATUS_INTERVAL_MS = 500
    private static final int LOG_ATTEMPTS = 10
    private static final int LOG_INTERVAL_MS = 1000
    private static final long MICROS_PER_MILLISECOND = 1000L
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0d
    private static final long MILLIS_PER_SECOND = 1000L
    private static final int RPS_PARAM_SCALE = 10
    private static final String RECOMMENDATION_PATH = '/api/recommendations'
    private static final String EMBEDDING_PATH = '/embeddings'
    private static final String EMBEDDING_CACHE = 'embeddingVectors'
    private static final String CHAT_COMPLETIONS_PATH = '/openai/v1/chat/completions'
    private static final String POOL_PREFIX = 'outbound_http_pool_'
    private static final String AUTH = 'Basic ' + Base64.encoder.encodeToString('admin:admin'.getBytes(StandardCharsets.UTF_8))
    private static final Duration CONTROLLER_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration METRICS_TIMEOUT = Duration.ofSeconds(15)
    private static final Duration EMBEDDING_METRICS_TIMEOUT = Duration.ofSeconds(5)
    private static final List<String> EMBEDDING_SERVER_FIELDS = List.of('encode_count', 'encode_seconds', 'http_count', 'http_seconds')
    private static final Set<String> TERMINAL = ['FINISHED', 'FINISHED_WITH_WARNING', 'STOP_BY_ERROR', 'CANCELED'] as Set
    private static final Pattern METRIC = ~/^([\w:]+)(?:\{([^}]*)})?\s+(\S+)(?:\s+.*)?$/
    private static final Pattern TAG = ~/(\w+)="([^"]*)"/
    private static final Pattern PERF = ~/PERF startMs=(\d+) endMs=(\d+) status=(\d+) latencyMicros=(\d+) valid=(true|false)/
    private static final String USAGE = '''
        사용법: ./gradlew measureRecommendation --args='<명령> [옵션]'
        명령: probe, diagnostics, run, mock-suite, pool-suite
        옵션: --port <번호> (probe, diagnostics), --mode <mock|real>, --rps <목표>,
              --threads <스레드>, --runs <반복>, --label <이름>, --embedding-metrics-url <URL>, --trace
        '''.stripIndent().trim()

    private final Path directory
    private final URI controller
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    private final JsonSlurper json = new JsonSlurper()

    private static final class Options {
        int port
        String mode
        BigDecimal rps
        int threads
        int runs
        String label
        String embeddingMetricsUrl
        boolean trace
    }

    private static final class RequestSample {
        long start
        long end
        int status
        BigDecimal latency
        boolean valid
    }

    /** 계산에 사용하는 타입과 결과 파일의 공개 필드 이름을 연결한다. */
    private static final class MetricsSnapshot {
        long timeMs
        final Map<String, Map<String, Number>> pools = [:]
        final Map<String, Map<String, Number>> calls = [:]
        final Map<String, Number> embeddingCache = [:]
        final Map<String, Number> apiServer = new LinkedHashMap<String, Number>([count: 0, sum: 0])
        final Map<String, Number> explanations = [:]
        Map<String, Number> embeddingServer
        Double cpu
        Double dbPending

        Map<String, Object> resources() {
            Map<String, Object> result = [time_ms: timeMs, pools: pools, calls: calls,
                embedding_cache: embeddingCache, api_server: apiServer]
            if (cpu != null) {
                result.put('cpu', cpu)
            }
            if (dbPending != null) {
                result.put('db_pending', dbPending)
            }
            if (embeddingServer != null) {
                result.put('embedding_server', embeddingServer)
            }
            return result
        }
    }

    private RunMeasurement(Path directory, URI controller) {
        this.directory = directory
        this.controller = controller
    }

    static RunMeasurement of(Path directory, URI controller) {
        return new RunMeasurement(directory, controller)
    }

    static void main(String[] arguments) {
        of(Path.of('ngrinder', 'recommendation'), LOCAL_CONTROLLER).execute(arguments)
    }

    /**
     * 단일 점검, 수동 조건 실행, 사전 정의된 부하 시나리오를 같은 수집 경로로 실행한다.
     */
    void execute(String[] arguments) {
        if (!arguments || arguments.contains('--help') || arguments.contains('-h')) {
            println USAGE
            return
        }

        Options options = parseOptions(arguments)
        switch (arguments[0]) {
            case 'probe':
                probe(options)
                break
            case 'diagnostics':
                diagnostics(options.port)
                break
            case 'run':
                run(options)
                break
            case ['mock-suite', 'pool-suite']:
                runSuite(options, arguments[0] == 'pool-suite')
                break
            default:
                throw new IllegalArgumentException("지원하지 않는 명령입니다.\n${USAGE}")
        }
    }

    private void runSuite(Options options, boolean poolSuite) {
        List<Options> stages = [
            new Options(rps: BigDecimal.ONE, threads: 1, runs: 5),
            new Options(rps: 8.3, threads: 20, runs: 13),
            new Options(rps: 16.7, threads: 30, runs: 17),
            new Options(rps: new BigDecimal('100'), threads: 100, runs: poolSuite ? 15 : 30),
        ]
        for (Options stage : stages) {
            stage.mode = 'mock'
            stage.label = options.label
            stage.embeddingMetricsUrl = options.embeddingMetricsUrl
            stage.trace = options.trace
            Map<String, Object> result = run(stage)
            if (!result.get('complete') || result.get('errors') || result.get('ngrinder_status') != 'FINISHED') {
                throw new IllegalStateException("실행 ${result.get('test_id')}의 검증에 실패했습니다. 남은 단계는 시작하지 않았습니다.")
            }
        }
    }

    /**
     * 값이 있는 옵션은 공백 구분과 등호 구분을 모두 지원한다.
     */
    private static Options parseOptions(String[] arguments) {
        Map<String, String> values = [port: Integer.toString(REAL_PORT), mode: 'mock', rps: '8.3',
            threads: '20', runs: '13', label: 'pool-check', 'embedding-metrics-url': '']
        boolean trace = false
        for (int index = 1; index < arguments.length; index++) {
            String flag = arguments[index]
            if (flag == '--trace') {
                trace = true
                continue
            }

            String[] parts = flag.split('=', 2)

            String key = parts[0].startsWith('--') ? parts[0].substring(2) : ''

            boolean separateValue = parts.length == 1

            if (!values.containsKey(key) ||
                    (separateValue && (index + 1 == arguments.length || arguments[index + 1].startsWith('--')))) {
                throw new IllegalArgumentException("옵션과 값을 확인해 주세요.\n${USAGE}")
            }

            values.put(key, separateValue ? arguments[++index] : parts[1])
        }

        Options options = new Options(
            port: Integer.parseInt(values.get('port')), mode: values.get('mode'), rps: new BigDecimal(values.get('rps')),
            threads: Integer.parseInt(values.get('threads')), runs: Integer.parseInt(values.get('runs')),
            label: values.get('label'), embeddingMetricsUrl: values.get('embedding-metrics-url'), trace: trace)

        if (!(options.mode in ['mock', 'real']) || options.port < 1 || options.port > 65535 ||
                options.threads < 1 || options.runs < 1 || options.rps < 0.1) {
            throw new IllegalArgumentException('모드, 포트, 스레드 수, 반복 횟수, 목표 RPS를 확인해 주세요.')
        }

        if (options.trace && arguments[0] == 'run' && options.mode != 'mock') {
            throw new IllegalArgumentException('--trace는 모의 부하에서만 사용할 수 있습니다.')
        }

        return options
    }

    private Map<String, Object> run(Options options) {
        int port = options.mode == 'mock' ? MOCK_PORT : REAL_PORT
        String embeddingMetricsUrl = options.embeddingMetricsUrl
        List<Map<String, Object>> metricsErrors = []
        MetricsSnapshot before = snapshot(port, embeddingMetricsUrl, 'before', metricsErrors)
        Path output = Files.createDirectories(directory.resolve('results'))
        int expected = Math.multiplyExact(options.threads, options.runs)
        String rate = rounded(options.rps * RPS_PARAM_SCALE, 0).toPlainString()
        String param = "${options.mode}_${rate}${options.trace ? '_trace' : ''}"
        Map<String, Object> fields = new LinkedHashMap<String, Object>([testName: "${options.label}-${options.mode}-${options.rps}rps-${options.threads}t-${options.runs}runs",
                      scriptName: 'RecommendationLoadTest.groovy', scm: 'svn', scriptRevision: -1,
                      status    : 'READY', threshold: 'R', runCount: options.runs, duration: TEST_TIMEOUT_SECONDS * MILLIS_PER_SECOND,
                      agentCount: 1, vuserPerAgent: options.threads, processes: 1, threads: options.threads,
                      region    : 'NONE', samplingInterval: 1, ignoreSampleCount: 0, param: param])
        Map<String, Object> model = parseJsonObject(controllerRequest('/perftest/api', fields))
        long testId = model.get('id') as long
        println JsonOutput.toJson([test_id: testId, expected_requests: expected])

        List<MetricsSnapshot> observations = []
        String previous = null
        long deadline = System.nanoTime() + Duration.ofSeconds(TEST_TIMEOUT_SECONDS).toNanos()

        try {
            while (System.nanoTime() < deadline) {
                model = parseJsonObject(controllerRequest("/perftest/api/${testId}"))
                Map<?, ?> controllerStatus = (Map<?, ?>) model.get('status')
                String status = (String) controllerStatus.get('name')
                if (status != previous) {
                    println JsonOutput.toJson([test_id: testId, status: status])
                    previous = status
                }

                MetricsSnapshot observation = collectMetrics(port, embeddingMetricsUrl, 'during', metricsErrors)
                if (observation != null) {
                    observations.add(observation)
                }

                if (status in TERMINAL) {
                    break
                }

                Thread.sleep(STATUS_INTERVAL_MS)
            }

            if (!(previous in TERMINAL)) {
                throw new IllegalStateException("nGrinder 실행 ${testId}가 ${TEST_TIMEOUT_SECONDS}초 안에 끝나지 않았습니다.")
            }

            MetricsSnapshot after = collectMetrics(port, embeddingMetricsUrl, 'after', metricsErrors)
            List<RequestSample> samples = downloadSamples(testId, output)

            if (!samples) {
                throw new IllegalStateException("실행 ${testId}의 요청 표본이 없습니다. ${model.get('progressMessage') ?: ''}")
            }

            Map<String, Object> result = summarize(testId, expected, options, previous, samples, observations, before, after)
            writeJson(output.resolve("${testId}-summary.json"), result)
            println JsonOutput.toJson(result)

            return result
        } finally {
            Map<String, Object> controllerInfo = model.subMap(['testName', 'status', 'progressMessage', 'lastProgressMessage'])

            if (metricsErrors) {
                controllerInfo.put('metrics_errors', metricsErrors)
            }

            writeJson(output.resolve("${testId}-controller.json"), controllerInfo)
            writeJson(output.resolve("${testId}-resources.json"), observations.collect { MetricsSnapshot sample -> sample.resources() })
        }
    }

    /** 부하 시작 후 지표 수집 실패를 기록하고 Controller 상태 조회와 로그 수집을 이어간다. */
    private MetricsSnapshot collectMetrics(int port, String embeddingMetricsUrl, String phase, List<Map<String, Object>> errors) {
        try {
            return snapshot(port, embeddingMetricsUrl, phase, errors)
        } catch (IOException | JsonException | NumberFormatException failure) {
            recordMetricsError('backend', phase, failure, errors)
            return null
        }
    }

    private static void recordMetricsError(String source, String phase, Exception failure, List<Map<String, Object>> errors) {
        Map<String, Object> error = new LinkedHashMap<String, Object>([
            source: source, phase: phase, time_ms: System.currentTimeMillis(), type: failure.class.simpleName])

        if (failure instanceof IOException) {
            error.put('message', failure.message)
        }

        errors.add(error)
        System.err.println(JsonOutput.toJson([metrics_error: error]))
    }

    private List<RequestSample> downloadSamples(long testId, Path output) {
        List<String> filenames = []
        for (int attempt = 0; attempt < LOG_ATTEMPTS; attempt++) {
            filenames = parseLogFilenames(controllerRequest("/perftest/api/${testId}/logs"))
            if (filenames) {
                break
            }
            Thread.sleep(LOG_INTERVAL_MS)
        }

        List<RequestSample> samples = []
        for (String filename : filenames) {
            if (filename != Path.of(filename).fileName?.toString() || filename in ['.', '..']) {
                throw new IllegalStateException('Controller가 잘못된 로그 파일명을 반환했습니다.')
            }

            String encoded = encode(filename).replace('+', '%20')
            byte[] archive = controllerRequest("/perftest/${testId}/download_log/${encoded}")
            Files.write(output.resolve("${testId}-${filename}"), archive)

            new ZipInputStream(new ByteArrayInputStream(archive)).withCloseable { ZipInputStream zip ->
                while (zip.nextEntry != null) {
                    Matcher rows = PERF.matcher(new String(zip.readAllBytes(), StandardCharsets.UTF_8))
                    while (rows.find()) {
                        samples.add(new RequestSample(start: rows.group(1).toLong(), end: rows.group(2).toLong(),
                            status: rows.group(3).toInteger(), latency: rows.group(4).toLong() / MICROS_PER_MILLISECOND,
                            valid: rows.group(5) == 'true'))
                    }
                }
            }
        }
        return samples
    }

    /**
     * 요청은 nearest-rank로 집계하고, 서버 지표는 측정 전후 누적 카운터의 차이로 계산한다.
     */
    private static Map<String, Object> summarize(long testId, int expected, Options options, String status,
                                                 List<RequestSample> samples, List<MetricsSnapshot> observations,
                                                 MetricsSnapshot before, MetricsSnapshot after) {
        long start = samples*.start.min()
        long end = samples*.end.max()
        double seconds = (end - start) / (double) MILLIS_PER_SECOND
        double arrivalSeconds = (samples*.start.max() - start) / (double) MILLIS_PER_SECOND + 1.0d / options.rps
        int success = samples.count { RequestSample sample -> sample.status == 200 && sample.valid }.intValue()
        List<BigDecimal> sorted = samples*.latency.sort()
        List<MetricsSnapshot> active = observations.findAll { MetricsSnapshot sample -> sample.timeMs >= start && sample.timeMs <= end }
        Map<String, Object> result = [test_id          : testId, mode: options.mode, target_rps: options.rps, samples: samples.size(), expected: expected,
                      complete         : samples.size() == expected, duration_seconds: rounded(seconds, 3),
                      arrival_rps      : rounded(samples.size() / arrivalSeconds, 3),
                      completed_rps    : seconds > 0 ? rounded(samples.size() / seconds, 3) : null,
                      success_tps      : seconds > 0 ? rounded(success / seconds, 3) : null, errors: samples.size() - success,
                      mean_ms          : rounded((sorted.sum() as BigDecimal) / sorted.size(), 2), p50_ms: percentile(sorted, 0.50d),
                      p95_ms           : percentile(sorted, 0.95d), p99_ms: percentile(sorted, 0.99d), max_ms: sorted.last(),
                      explanations     : after == null ? null : difference(before.explanations, after.explanations),
                      ngrinder_status  : status,
                      label            : options.label, pool_metrics: poolMetrics(before.pools, active),
                      cpu_max          : active.collect { MetricsSnapshot sample -> sample.cpu ?: 0.0d }.max(),
                      db_pending_max   : active.collect { MetricsSnapshot sample -> sample.dbPending ?: 0.0d }.max(),
                      monitor_samples  : active.size(),
                      http_client_calls: null, http_client_mean_ms: null,
                      api_server_count : null, api_server_mean_ms: null]

        if (after == null) {
            return result
        }

        Map<String, Number> clientCalls = [:]
        Map<String, Number> clientMeans = [:]
        for (Map.Entry<String, Map<String, Number>> entry : after.calls.entrySet()) {
            String uri = entry.key
            Map<String, Number> total = entry.value
            Map<String, Number> old = before.calls.get(uri) ?: [:]
            Number count = counterDifference(total.get('count'), old.getOrDefault('count', 0))

            clientCalls.put(uri, count)
            clientMeans.put(uri, meanMillis(counterDifference(total.get('sum'), old.getOrDefault('sum', 0)), count))
        }
        result.put('http_client_calls', clientCalls)
        result.put('http_client_mean_ms', clientMeans)
        if (after.embeddingCache) {
            Map<String, Number> cache = difference(before.embeddingCache, after.embeddingCache)
            Number hits = cache.getOrDefault('hit', 0)
            Number requests = hits + cache.getOrDefault('miss', 0)
            cache.put('hit_ratio', requests ? rounded(hits / requests, 6) : null)
            result.put('embedding_cache', cache)
        }

        Number serverCount = counterDifference(after.apiServer.get('count'), before.apiServer.get('count'))
        result.put('api_server_count', serverCount)
        result.put('api_server_mean_ms', meanMillis(counterDifference(after.apiServer.get('sum'), before.apiServer.get('sum')), serverCount))
        Map<String, Number> delta = embeddingServerDifference(before.embeddingServer, after.embeddingServer, observations*.embeddingServer)

        if (delta != null) {
            Map<String, Number> embeddingMeans = [:]
            for (String name : ['encode', 'http']) {
                String countKey = name + '_count'
                Number count = delta.get(countKey)
                embeddingMeans.put(name, meanMillis(delta.get(name + '_seconds'), count))
                embeddingMeans.put(countKey, count)
            }

            result.put('embedding_server_mean_ms', embeddingMeans)
        }

        return result
    }

    private static Map<String, Map<String, Object>> poolMetrics(Map<String, Map<String, Number>> pools, List<MetricsSnapshot> active) {
        Map<String, Map<String, Object>> results = [:]

        for (Map.Entry<String, Map<String, Number>> pool : pools.entrySet()) {
            String name = pool.key
            Map<String, Object> values = [:]
            values.put('configured', pool.value)

            for (String metric : ['leased', 'pending', 'available']) {
                List<Number> observed = active.collect { MetricsSnapshot sample -> sample.pools.get(name)?.get(metric) ?: 0 }
                values.put(metric + '_max', observed.max())
                values.put(metric + '_mean', observed ? rounded((observed.sum() as Number) / observed.size(), 3) : null)
            }

            results.put(name, values)
        }

        return results
    }

    private static BigDecimal percentile(List<BigDecimal> sorted, double fraction) {
        return sorted[(int) Math.ceil(sorted.size() * fraction) - 1]
    }

    private static BigDecimal meanMillis(Number seconds, Number count) {
        if (!count) {
            return null
        }

        if (seconds instanceof Double || count instanceof Double) {
            return rounded(seconds.doubleValue() * MILLIS_PER_SECOND / count.doubleValue(), 3)
        }

        return rounded(new BigDecimal(seconds.toString()) * MILLIS_PER_SECOND / new BigDecimal(count.toString()), 3)
    }

    private static BigDecimal rounded(Number value, int scale) {
        return new BigDecimal(value.toString()).setScale(scale, RoundingMode.HALF_EVEN)
    }

    private static Map<String, Number> difference(Map<String, Number> before, Map<String, Number> after) {
        Map<String, Number> delta = [:]

        for (String key : before.keySet() + after.keySet()) {
            delta.put(key, counterDifference(after.getOrDefault(key, 0), before.getOrDefault(key, 0)))
        }

        return delta
    }

    private static Number counterDifference(Number after, Number before) {
        if (after instanceof Double || before instanceof Double) {
            return after.doubleValue() - before.doubleValue()
        }

        return new BigDecimal(after.toString()).subtract(new BigDecimal(before.toString()))
    }

    /**
     * 시작과 종료 표본이 있고 관측된 누적 카운터가 감소하지 않았을 때만 차이를 계산한다.
     */
    private static Map<String, Number> embeddingServerDifference(Map<String, Number> before, Map<String, Number> after,
                                                                 List<Map<String, Number>> intermediate = []) {
        if (before == null || after == null) {
            return null
        }

        Map<String, Number> previous = before
        for (Map<String, Number> sample : intermediate + [after]) {
            if (sample == null) {
                continue
            }

            if (EMBEDDING_SERVER_FIELDS.any { String field -> sample.get(field) < previous.get(field) }) {
                return null
            }
            previous = sample
        }
        return difference(before, after)
    }

    /** 백엔드 표본을 수집하고, 선택 임베딩 지표의 실패는 백엔드 데이터와 별도로 기록한다. */
    private MetricsSnapshot snapshot(int port, String embeddingMetricsUrl, String phase, List<Map<String, Object>> errors) {
        String text = new String(successful(get(backend(port, '/actuator/prometheus'), METRICS_TIMEOUT)), StandardCharsets.UTF_8)
        MetricsSnapshot resources = new MetricsSnapshot(timeMs: System.currentTimeMillis())

        for (String line : text.readLines()) {
            Matcher matcher = METRIC.matcher(line)

            if (!matcher.matches()) {
                continue
            }

            String name = matcher.group(1)
            String measurement = matcher.group(3)
            if (measurement in ['NaN', '+Inf', '-Inf']) {
                continue
            }

            double value = Double.parseDouble(measurement)
            Map<String, String> tags = [:]
            Matcher labels = TAG.matcher(matcher.group(2) ?: '')

            while (labels.find()) {
                tags.put(labels.group(1), labels.group(2))
            }

            switch (name) {
                case 'recommendation_explanations_total':
                    resources.explanations.put("${tags.get('outcome')}:${tags.get('reason')}".toString(), value)
                    break
                case ~/^outbound_http_pool_.+$/:
                    Map<String, Number> pool = resources.pools.computeIfAbsent(tags.get('client')) { String key -> new LinkedHashMap<String, Number>() }
                    pool.put(name.substring(POOL_PREFIX.length()), value)
                    break
                case ['http_client_requests_seconds_count', 'http_client_requests_seconds_sum']:
                    String uri = tags.get('uri')
                    if (uri in [EMBEDDING_PATH, CHAT_COMPLETIONS_PATH]) {
                        String kind = name.endsWith('_count') ? 'count' : 'sum'
                        Map<String, Number> totals = resources.calls.computeIfAbsent(uri) { String key -> [count: 0, sum: 0] as Map<String, Number> }
                        totals.put(kind, totals.get(kind).doubleValue() + value)
                    }
                    break
                case ['http_server_requests_seconds_count', 'http_server_requests_seconds_sum']:
                    if (tags.get('uri') == RECOMMENDATION_PATH) {
                        String kind = name.endsWith('_count') ? 'count' : 'sum'
                        resources.apiServer.put(kind, resources.apiServer.get(kind).doubleValue() + value)
                    }
                    break
                case 'cache_gets_total':
                    if (tags.get('cache') == EMBEDDING_CACHE) {
                        resources.embeddingCache.put(tags.get('result'), value)
                    }
                    break
                case 'cache_load_total':
                    if (tags.get('cache') == EMBEDDING_CACHE) {
                        resources.embeddingCache.put('load_' + tags.get('result'), value)
                    }
                    break
                case 'cache_load_duration_seconds':
                    if (tags.get('cache') == EMBEDDING_CACHE) {
                        resources.embeddingCache.put('load_seconds', value)
                    }
                    break
                case 'cache_evictions_total':
                    if (tags.get('cache') == EMBEDDING_CACHE) {
                        resources.embeddingCache.put('evictions', value)
                    }
                    break
                case 'process_cpu_usage':
                    resources.cpu = value
                    break
                case 'hikaricp_connections_pending':
                    resources.dbPending = value
                    break
            }
        }
        if (embeddingMetricsUrl) {
            try {
                Map<String, Object> metrics = parseJsonObject(successful(get(URI.create(embeddingMetricsUrl), EMBEDDING_METRICS_TIMEOUT)))
                Map<String, Number> embeddingMetrics = [:]
                for (String field : EMBEDDING_SERVER_FIELDS) {
                    Object value = metrics.get(field)
                    if (!(value instanceof Number)) {
                        throw new JsonException('임베딩 지표 응답에 누적 요청 수 또는 시간이 없습니다.')
                    }
                    embeddingMetrics.put(field, (Number) value)
                }
                resources.embeddingServer = embeddingMetrics
            } catch (IOException | JsonException failure) {
                recordMetricsError('embedding_server', phase, failure, errors)
            }
        }
        return resources
    }

    private void probe(Options options) {
        int port = options.port
        String embeddingMetricsUrl = options.embeddingMetricsUrl
        List<Map<String, Object>> metricsErrors = []
        MetricsSnapshot before = snapshot(port, embeddingMetricsUrl, 'before', metricsErrors)
        Properties settings = new Properties()
        Files.newBufferedReader(directory.resolve('target.properties')).withCloseable { Reader reader -> settings.load(reader) }
        String token = settings.getProperty('accessToken', '').trim()
        String weatherId = settings.getProperty('weatherId', '').trim()

        if (!token || !weatherId) {
            throw new IllegalArgumentException('target.properties의 accessToken과 weatherId를 확인해 주세요.')
        }

        URI endpoint = backend(port, "${RECOMMENDATION_PATH}?weatherId=${encode(weatherId)}")
        long started = System.nanoTime()
        HttpResponse<byte[]> response = get(endpoint, CONTROLLER_TIMEOUT, [Authorization: 'Bearer ' + token])
        Map<String, Object> body = parseJsonObject(successful(response))
        double latencyMillis = (System.nanoTime() - started) / NANOS_PER_MILLISECOND
        List<?> clothes = (List<?>) (body.get('clothes') ?: [])
        List<?> sources = (List<?>) (body.get('sources') ?: [])

        println JsonOutput.toJson([status : response.statusCode(), latency_ms: Math.round(latencyMillis),
            clothes: clothes.size(), sources: sources.size()])

        MetricsSnapshot after = snapshot(port, embeddingMetricsUrl, 'after', metricsErrors)

        println JsonOutput.toJson([explanations: after.explanations])

        Map<String, Number> embeddingDelta = embeddingServerDifference(before.embeddingServer, after.embeddingServer)
        Number afterCalls = after.calls.get(EMBEDDING_PATH)?.get('count') ?: 0
        Number beforeCalls = before.calls.get(EMBEDDING_PATH)?.get('count') ?: 0

        println JsonOutput.toJson([probe_resources: [embedding_cache: difference(before.embeddingCache, after.embeddingCache),
            embedding_http_calls: afterCalls - beforeCalls, embedding_server_encode_count: embeddingDelta?.get('encode_count')]])
    }

    private void diagnostics(int port) {
        Map<String, Object> result = [:]
        List<List<String>> metrics = [
            List.of('http.client.requests', 'uri:' + EMBEDDING_PATH),
            List.of('http.client.requests', 'uri:/chat/completions'),
            List.of('http.client.requests', 'uri:' + CHAT_COMPLETIONS_PATH),
            List.of('process.cpu.usage'),
            List.of('hikaricp.connections.pending'),
        ]

        for (List<String> entry : metrics) {
            String name = entry[0]
            List<String> tags = entry.drop(1)
            String query = tags ? '?tag=' + encode(tags[0]) : ''
            HttpResponse<byte[]> response = get(backend(port, "/actuator/metrics/${name}${query}"), METRICS_TIMEOUT)
            String key = name + '[' + tags.collect { String tag -> "'${tag}'" }.join(', ') + ']'

            result.put(key, response.statusCode() == 200 ? parseJsonObject(response.body()).get('measurements') : [status: response.statusCode()])
        }
        println JsonOutput.toJson(result)
    }

    private byte[] controllerRequest(String path, Map<String, Object> fields = null) {
        HttpRequest.Builder request = HttpRequest.newBuilder(controller.resolve(path)).timeout(CONTROLLER_TIMEOUT)
                .header('Authorization', AUTH)

        if (fields != null) {
            String data = fields.collect { String key, Object value -> "${encode(key)}=${encode(value.toString())}" }.join('&')
            request.header('Content-Type', 'application/x-www-form-urlencoded')
                    .POST(HttpRequest.BodyPublishers.ofString(data))
        }

        return successful(client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray()))
    }

    private HttpResponse<byte[]> get(URI url, Duration timeout, Map<String, String> headers = [:]) {
        HttpRequest.Builder request = HttpRequest.newBuilder(url).timeout(timeout)
        headers.each { String name, String value -> request.header(name, value) }

        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private static byte[] successful(HttpResponse<byte[]> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP ${response.statusCode()} ${response.request().uri().path}")
        }

        return response.body()
    }

    /** 외부 JSON의 형태를 확인한 뒤 스크립트에서 사용할 타입으로 변환한다. */
    private Map<String, Object> parseJsonObject(byte[] body) {
        Object parsed = json.parse(body, StandardCharsets.UTF_8.name())
        if (!(parsed instanceof Map)) {
            throw new JsonException('JSON 응답이 객체 형식이 아닙니다.')
        }

        return (Map<String, Object>) parsed
    }

    private List<String> parseLogFilenames(byte[] body) {
        Object parsed = json.parse(body, StandardCharsets.UTF_8.name())
        if (!(parsed instanceof List)) {
            throw new JsonException('Controller 로그 목록이 배열 형식이 아닙니다.')
        }

        List<String> filenames = []

        for (Object entry : parsed) {
            if (!(entry instanceof String)) {
                throw new JsonException('Controller 로그 목록에 문자열이 아닌 파일명이 있습니다.')
            }

            filenames.add((String) entry)
        }

        return filenames
    }

    private static void writeJson(Path path, Object data) {
        Files.writeString(path, JsonOutput.prettyPrint(JsonOutput.toJson(data)) + '\n')
    }

    private static URI backend(int port, String path) {
        return URI.create("http://127.0.0.1:${port}${path}")
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
