package codeit.ootd.infra.embedding;

import java.util.List;

public record EmbeddingResponse(
        String model,
        int dimensions,
        List<Double> embedding
) {
}
