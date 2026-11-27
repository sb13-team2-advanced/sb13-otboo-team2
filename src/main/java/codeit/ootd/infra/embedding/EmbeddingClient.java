package codeit.ootd.infra.embedding;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EmbeddingClient {

    static final String EXPECTED_MODEL =
            "intfloat/multilingual-e5-small";
    private static final int EXPECTED_DIMENSIONS = 384;

    private final RestClient restClient;

    public EmbeddingClient(
            @Qualifier("embeddingRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
    }

    /**
     * 검색 문장을 임베딩 서버에 전달하고 모델·차원·벡터 값을 검증한다.
     */
    public EmbeddingResponse embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("검색 문장이 비어 있습니다.");
        }

        EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text.strip()))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.embedding() == null) {
            throw new IllegalStateException("임베딩 응답이 없습니다.");
        }

        if (!EXPECTED_MODEL.equals(response.model())) {
            throw new IllegalStateException("임베딩 모델이 일치하지 않습니다.");
        }

        if (response.dimensions() != EXPECTED_DIMENSIONS
                || response.embedding().size() != EXPECTED_DIMENSIONS) {
            throw new IllegalStateException("임베딩 차원이 일치하지 않습니다.");
        }

        boolean invalidValue = response.embedding().stream()
                .anyMatch(value -> value == null || !Double.isFinite(value));

        if (invalidValue) {
            throw new IllegalStateException("임베딩에 유효하지 않은 값이 있습니다.");
        }

        if (response.embedding().stream().allMatch(value -> value == 0.0)) {
            throw new IllegalStateException("임베딩이 영벡터입니다.");
        }

        return response;
    }
}
