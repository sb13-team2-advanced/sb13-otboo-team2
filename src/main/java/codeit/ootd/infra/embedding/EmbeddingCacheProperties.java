package codeit.ootd.infra.embedding;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 임베딩 캐시의 크기·만료와 모델 및 전처리 변경 시 사용할 버전 공간을 정의한다. */
@ConfigurationProperties("ootd.embedding.cache")
public record EmbeddingCacheProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("512") long maximumSize,
    @DefaultValue("24h") Duration ttl,
    @DefaultValue("v1") String modelRevision,
    @DefaultValue("query-normalized-v1") String preprocessingVersion
) {

    public EmbeddingCacheProperties {
        if (maximumSize < 1 || ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("임베딩 캐시 크기와 만료 시간은 양수여야 합니다.");
        }

        if (modelRevision == null || modelRevision.isBlank()
            || preprocessingVersion == null || preprocessingVersion.isBlank()) {
            throw new IllegalArgumentException("임베딩 캐시의 모델·전처리 버전이 필요합니다.");
        }
    }
}
