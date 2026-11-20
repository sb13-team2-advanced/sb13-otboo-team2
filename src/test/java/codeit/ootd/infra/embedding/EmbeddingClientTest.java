package codeit.ootd.infra.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class EmbeddingClientTest {

    private static final String MODEL = "intfloat/multilingual-e5-small";
    private static final int DIMENSIONS = 384;
    private static final String URL = "http://embedding.test/embeddings";
    private static final String VECTOR = Collections.nCopies(DIMENSIONS, 0.1).toString();

    private MockRestServiceServer server;
    private EmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EmbeddingClient(builder.baseUrl("http://embedding.test").build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void 검색_문장의_공백을_정리해_POST로_전달하고_벡터를_반환한다() {
        // given
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"text\":\"오렌지 톤온톤\"}"))
            .andRespond(withSuccess(response(MODEL, DIMENSIONS, VECTOR), MediaType.APPLICATION_JSON));

        // when
        EmbeddingResponse result = client.embed("  오렌지 톤온톤  ");

        // then
        assertThat(result.model()).isEqualTo(MODEL);
        assertThat(result.dimensions()).isEqualTo(DIMENSIONS);
        assertThat(result.embedding()).hasSize(DIMENSIONS).containsOnly(0.1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 빈_검색_문장이면_HTTP를_호출하지_않는다(String text) {
        // given/when/then
        assertThatThrownBy(() -> client.embed(text))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("검색 문장이 비어 있습니다.");
    }

    @Test
    void 응답_본문이_없으면_실패한다() {
        // given
        server.expect(requestTo(URL)).andRespond(withNoContent());

        // when/then
        assertThatThrownBy(() -> client.embed("오렌지"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("임베딩 응답이 없습니다.");
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void 유효하지_않은_임베딩_응답을_거부한다(String body, String message) {
        // given
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // when/then
        assertThatThrownBy(() -> client.embed("오렌지"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(message);
    }

    @Test
    void 서버의_HTTP_오류를_호출자에게_전달한다() {
        // given
        server.expect(requestTo(URL)).andRespond(withServerError());

        // when/then
        assertThatThrownBy(() -> client.embed("오렌지"))
            .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void 응답_시간_초과를_호출자에게_전달한다() {
        // given
        server.expect(requestTo(URL))
            .andRespond(withException(new SocketTimeoutException("read timed out")));

        // when/then
        assertThatThrownBy(() -> client.embed("오렌지"))
            .isInstanceOf(ResourceAccessException.class)
            .hasCauseInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void JSON을_해석할_수_없으면_실패한다() {
        // given
        server.expect(requestTo(URL))
            .andRespond(withSuccess("{invalid", MediaType.APPLICATION_JSON));

        // when/then
        assertThatThrownBy(() -> client.embed("오렌지"))
            .isInstanceOf(RestClientException.class);
    }

    private static Stream<Arguments> invalidResponses() {
        return Stream.of(
            Arguments.of(response(MODEL, DIMENSIONS, "null"), "임베딩 응답이 없습니다."),
            Arguments.of(response("other-model", DIMENSIONS, VECTOR), "임베딩 모델이 일치하지 않습니다."),
            Arguments.of(response(MODEL, 3, VECTOR), "임베딩 차원이 일치하지 않습니다."),
            Arguments.of(response(MODEL, DIMENSIONS, "[0.1]"), "임베딩 차원이 일치하지 않습니다."),
            Arguments.of(response(MODEL, DIMENSIONS, vectorWithFirst("null")), "임베딩에 유효하지 않은 값이 있습니다."),
            Arguments.of(response(MODEL, DIMENSIONS, vectorWithFirst("\"NaN\"")), "임베딩에 유효하지 않은 값이 있습니다."),
            Arguments.of(response(MODEL, DIMENSIONS, vectorWithFirst("\"Infinity\"")), "임베딩에 유효하지 않은 값이 있습니다."),
            Arguments.of(response(MODEL, DIMENSIONS, Collections.nCopies(DIMENSIONS, 0.0).toString()), "임베딩이 영벡터입니다.")
        );
    }

    private static String response(String model, int dimensions, String vector) {
        return """
            {"model":"%s","dimensions":%d,"embedding":%s}
            """.formatted(model, dimensions, vector);
    }

    private static String vectorWithFirst(String first) {
        return "[" + first + "," + String.join(",", Collections.nCopies(DIMENSIONS - 1, "0.1")) + "]";
    }
}
