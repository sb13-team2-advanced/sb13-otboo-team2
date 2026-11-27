package codeit.ootd.infra.embedding;

import java.util.List;

public record EmbeddingResponse(
        String model,
        int dimensions,
        List<Double> embedding
) {

    /** 검증이 끝난 응답의 벡터를 복사해 캐시에서 안전하게 공유한다. */
    public static EmbeddingResponse copyOf(EmbeddingResponse response) {
        return new EmbeddingResponse(response.model(), response.dimensions(), List.copyOf(response.embedding()));
    }
}
