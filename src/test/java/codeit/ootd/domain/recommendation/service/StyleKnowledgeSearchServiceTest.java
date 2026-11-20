package codeit.ootd.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.recommendation.enums.CombinationType;
import codeit.ootd.domain.recommendation.repository.StyleKnowledgeRepository;
import codeit.ootd.infra.embedding.EmbeddingClient;
import codeit.ootd.infra.embedding.EmbeddingResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class StyleKnowledgeSearchServiceTest {

    private static final EmbeddingResponse EMBEDDING = new EmbeddingResponse(
        "intfloat/multilingual-e5-small", 384, Collections.nCopies(384, 0.1));

    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private StyleKnowledgeRepository repository;

    private StyleKnowledgeSearchService service;

    @BeforeEach
    void setUp() {
        service = new StyleKnowledgeSearchService(embeddingClient, repository, false);
    }

    @Test
    void 문장과_기준색을_정리하고_승인_문서를_최대_세_개_검색한다() {
        // given
        given(embeddingClient.embed("오렌지 코디")).willReturn(EMBEDDING);
        given(repository.search(EMBEDDING, "오렌지", CombinationType.TONAL, false, 3))
            .willReturn(List.of());

        // when
        assertThat(service.search("  오렌지 코디  ", " 오렌지 ", CombinationType.TONAL)).isEmpty();

        // then
        verify(repository).search(EMBEDDING, "오렌지", CombinationType.TONAL, false, 3);
    }

    @Test
    void 개발용_초안_포함_설정과_생략한_배색_유형을_전달한다() {
        // given
        StyleKnowledgeSearchService developmentService =
            new StyleKnowledgeSearchService(embeddingClient, repository, true);
        given(embeddingClient.embed("오렌지 코디")).willReturn(EMBEDDING);

        // when
        developmentService.search("오렌지 코디", "오렌지", null);

        // then
        verify(repository).search(EMBEDDING, "오렌지", null, true, 3);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 빈_문장은_외부_호출_전에_거부한다(String text) {
        // given/when/then
        assertThatThrownBy(() -> service.search(text, "오렌지", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("검색 문장이 비어 있습니다.");
        verifyNoInteractions(embeddingClient, repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 빈_기준색은_외부_호출_전에_거부한다(String baseColor) {
        // given/when/then
        assertThatThrownBy(() -> service.search("코디", baseColor, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("기준색이 비어 있습니다.");
        verifyNoInteractions(embeddingClient, repository);
    }

    @Test
    void 임베딩_호출이_실패하면_DB를_조회하지_않는다() {
        // given
        given(embeddingClient.embed("오렌지 코디"))
            .willThrow(new ResourceAccessException("임베딩 서버 연결 실패"));

        // when/then
        assertThatThrownBy(() -> service.search("오렌지 코디", "오렌지", null))
            .isInstanceOf(ResourceAccessException.class);
        verifyNoInteractions(repository);
    }
}
