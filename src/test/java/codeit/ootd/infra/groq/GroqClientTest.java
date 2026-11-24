package codeit.ootd.infra.groq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

class GroqClientTest {

    private static final String URL = "https://groq.test/chat/completions";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private RestClient restClient;
    private GroqClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://groq.test");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new GroqClient(restClient, JSON, true, "test-key", "openai/gpt-oss-20b");
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void 인증과_모델과_구조화_응답_요청을_전달하고_설명과_근거를_반환한다() {
        // given
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
            .andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
            .andExpect(jsonPath("$.messages[0].role").value("system"))
            .andExpect(jsonPath("$.messages[1].content").value("입력"))
            .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
            .andExpect(jsonPath("$.max_completion_tokens").value(768))
            .andExpect(jsonPath("$.reasoning_effort").value("low"))
            .andRespond(withSuccess(envelope(validPayload()), MediaType.APPLICATION_JSON));

        // when
        GroqExplanation result = client.generate("지시", "입력");

        // then
        assertThat(result.explanation()).isEqualTo("두 의상의 색상이 배색 사례와 일치합니다.");
        assertThat(result.documentKey()).isEqualTo("guide");
        assertThat(result.matchedClothesIds()).containsExactly("top", "bottom");
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "missing-key"})
    void 비활성화되거나_키가_없으면_외부로_요청하지_않는다(String mode) {
        // given
        GroqClient unconfigured = new GroqClient(restClient, JSON,
            !mode.equals("disabled"), mode.equals("missing-key") ? " " : "key", "openai/gpt-oss-20b");

        // when/then
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.generate("지시", "입력")).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void 비정상_완료와_잘못된_설명_응답을_거부한다(String body) {
        // given
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // when/then
        assertThatThrownBy(() -> client.generate("지시", "입력")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 응답이_없으면_기본_설명을_선택할_수_있도록_실패를_전달한다() {
        // given
        server.expect(requestTo(URL)).andRespond(withNoContent());

        // when/then
        assertThatThrownBy(() -> client.generate("지시", "입력")).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void 인증과_한도와_서버_오류는_재시도하지_않고_전달한다(int status) {
        // given
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        // when/then
        assertThatThrownBy(() -> client.generate("지시", "입력")).isInstanceOf(RestClientException.class);
    }

    @Test
    void 시간_초과를_호출_서비스에_전달한다() {
        // given
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("timeout")));

        // when/then
        assertThatThrownBy(() -> client.generate("지시", "입력")).isInstanceOf(RestClientException.class);
    }

    @ParameterizedTest
    @CsvSource({"tokens per minute,TPM", "requests per minute,RPM", "tokens per day,TPD", "requests per day,RPD", "other,UNKNOWN"})
    void 제한_종류와_숫자만_기록하고_조직과_오류_원문은_노출하지_않는다(String dimension, String expected) {
        // given
        Logger logger = (Logger) LoggerFactory.getLogger(GroqClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String body = JSON.writeValueAsString(Map.of("error", Map.of("message",
            "Rate limit on " + dimension + ": Limit 8000 org_private test-key")));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
            .contentType(MediaType.APPLICATION_JSON).body(body));

        try {
            // when
            assertThatThrownBy(() -> client.generate("지시", "입력")).isInstanceOf(RestClientException.class);

            // then
            assertThat(appender.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("dimension=" + expected, "limit=8000")
                    .doesNotContain("org_private", "test-key", body));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static Stream<String> invalidResponses() {
        return Stream.of("null", "{}", "{\"choices\":[]}",
            envelope(validPayload()).replace("\"stop\"", "\"length\""),
            "{\"error\":{\"message\":\"failed\"}}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null}}]}",
            envelope("{"), envelope("null"), envelope("{}"),
            envelope(JSON.writeValueAsString(Map.of("explanation", " ", "documentKey", "guide", "matchedClothesIds", List.of("top")))),
            envelope(JSON.writeValueAsString(Map.of("explanation", "English only", "documentKey", "guide", "matchedClothesIds", List.of("top")))),
            envelope(JSON.writeValueAsString(Map.of("explanation", "한".repeat(501), "documentKey", "guide", "matchedClothesIds", List.of("top")))),
            envelope(JSON.writeValueAsString(Map.of("explanation", "설명", "documentKey", " ", "matchedClothesIds", List.of("top")))),
            envelope(JSON.writeValueAsString(Map.of("explanation", "설명", "documentKey", "guide", "matchedClothesIds", List.of()))),
            envelope(JSON.writeValueAsString(Map.of("explanation", "설명", "documentKey", "guide", "matchedClothesIds", List.of(" ")))));
    }

    private static String validPayload() {
        return JSON.writeValueAsString(Map.of("explanation", "두 의상의 색상이 배색 사례와 일치합니다.",
            "documentKey", "guide", "matchedClothesIds", List.of("top", "bottom")));
    }

    private static String envelope(String payload) {
        return JSON.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop",
            "message", Map.of("content", payload)))));
    }
}
