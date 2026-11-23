package codeit.ootd.domain.recommendation.service;

import codeit.ootd.domain.recommendation.dto.response.StyleKnowledgeMatch;
import codeit.ootd.domain.recommendation.enums.CombinationType;
import codeit.ootd.domain.recommendation.repository.StyleKnowledgeRepository;
import codeit.ootd.infra.embedding.EmbeddingClient;
import codeit.ootd.infra.embedding.EmbeddingResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StyleKnowledgeSearchService {

    private static final int SEARCH_LIMIT = 3;

    private final EmbeddingClient embeddingClient;
    private final StyleKnowledgeRepository repository;
    private final boolean includeDrafts;

    public StyleKnowledgeSearchService(
        EmbeddingClient embeddingClient,
        StyleKnowledgeRepository repository,
        @Value("${ootd.recommendation.knowledge.include-drafts:false}") boolean includeDrafts
    ) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.includeDrafts = includeDrafts;
    }

    /**
     * 검색 문장과 기준색을 검증하고 외부 임베딩 응답을 Repository에 전달한다.
     * 배색 유형을 생략하면 같은 기준색의 모든 유형을 비교하며 최대 세 문서를 반환한다.
     * 이 서비스는 트랜잭션을 시작하지 않으며 Repository 조회에 읽기 전용 트랜잭션을 적용한다.
     */
    public List<StyleKnowledgeMatch> search(
        String text,
        String baseColor,
        CombinationType combinationType
    ) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("검색 문장이 비어 있습니다.");
        }
        if (baseColor == null || baseColor.isBlank()) {
            throw new IllegalArgumentException("기준색이 비어 있습니다.");
        }

        EmbeddingResponse embedding = embeddingClient.embed(text.strip());
        return repository.search(embedding, baseColor.strip(), combinationType,
            includeDrafts, SEARCH_LIMIT);
    }
}
