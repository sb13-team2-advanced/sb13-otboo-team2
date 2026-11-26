package codeit.ootd.infra.embedding;

/** 같은 문장도 모델·가중치 버전·전처리 계약이 다르면 별도 캐시 항목으로 구분한다. */
record EmbeddingCacheKey(String model, String modelRevision, String preprocessingVersion, String text) {

    static EmbeddingCacheKey of(EmbeddingCacheProperties properties, String text) {
        return new EmbeddingCacheKey(EmbeddingClient.EXPECTED_MODEL, properties.modelRevision(),
            properties.preprocessingVersion(), text);
    }
}
