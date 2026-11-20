package codeit.ootd.domain.recommendation.repository;

import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import codeit.ootd.domain.recommendation.enums.CombinationType;
import codeit.ootd.domain.recommendation.mapper.StyleKnowledgeMetadataParser;
import codeit.ootd.infra.embedding.EmbeddingResponse;
import java.sql.Types;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class StyleKnowledgeRepository {

    private static final String SEARCH_SQL = """
        SELECT document_key, title, content, base_color, combination_type,
               source_type, source_url, metadata::text AS metadata_json,
               embedding <=> CAST(:embedding AS vector) AS distance
        FROM style_knowledge
        WHERE embedding_model = :model
          AND embedding IS NOT NULL
          AND base_color = :baseColor
          AND (:combinationType IS NULL OR combination_type = :combinationType)
          AND (review_status = 'APPROVED'
               OR (:includeDrafts AND review_status = 'DRAFT'))
        ORDER BY distance, document_key
        LIMIT :limit
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final StyleKnowledgeMetadataParser metadataParser;

    /**
     * 검증된 임베딩과 동일한 모델의 문서를 기준색·배색 유형·검토 상태로 제한한 뒤
     * 코사인 거리순으로 정확 검색한다. 동점은 문서 키로 정렬하고 벡터는 SQL 매개변수로 전달한다.
     * JSONB 메타데이터는 파서에서 검증한 색 배치 목록으로 변환한다.
     */
    @Transactional(readOnly = true)
    public List<StyleKnowledgeMatch> search(
        EmbeddingResponse embedding,
        String baseColor,
        CombinationType combinationType,
        boolean includeDrafts,
        int limit
    ) {
        if (limit < 1) {
            throw new IllegalArgumentException("검색 개수는 1 이상이어야 합니다.");
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("embedding", embedding.embedding().toString())
            .addValue("model", embedding.model())
            .addValue("baseColor", baseColor)
            .addValue("combinationType",
                combinationType == null ? null : combinationType.name(), Types.VARCHAR)
            .addValue("includeDrafts", includeDrafts)
            .addValue("limit", limit);

        return jdbcTemplate.query(SEARCH_SQL, parameters, (resultSet, rowNumber) ->
            new StyleKnowledgeMatch(
                resultSet.getString("document_key"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("base_color"),
                CombinationType.valueOf(resultSet.getString("combination_type")),
                resultSet.getString("source_type"),
                resultSet.getString("source_url"),
                metadataParser.parse(resultSet.getString("document_key"), resultSet.getString("metadata_json")),
                1.0 - resultSet.getDouble("distance")
            ));
    }
}
