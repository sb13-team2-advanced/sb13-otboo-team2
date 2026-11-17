package codeit.ootd.domain.feed.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import codeit.ootd.domain.feed.entity.FeedClothes;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("피드 의상 연결 매핑 테스트")
class FeedClothesMapperTest {

    private final FeedMapper feedMapper =
            Mappers.getMapper(FeedMapper.class);

    @Test
    @DisplayName("선택한 의상을 같은 피드에 연결하고 요청 순서대로 표시 순서를 지정한다")
    void createsLinksInRequestedOrder() {
        UUID feedId = UUID.randomUUID();
        UUID topId = UUID.randomUUID();
        UUID bottomId = UUID.randomUUID();
        UUID shoesId = UUID.randomUUID();

        List<UUID> clothesIds = List.of(topId, bottomId, shoesId);

        List<FeedClothes> result =
                feedMapper.toFeedClothes(feedId, clothesIds);

        assertThat(result)
                .extracting(
                        FeedClothes::getFeedId,
                        FeedClothes::getClothesId,
                        FeedClothes::getDisplayOrder
                )
                .containsExactly(
                        tuple(feedId, topId, 0),
                        tuple(feedId, bottomId, 1),
                        tuple(feedId, shoesId, 2)
                );
    }

    @Test
    @DisplayName("의상 목록이 비어 있으면 연결 데이터도 빈 목록으로 반환한다")
    void returnsEmptyListWhenClothesAreEmpty() {
        List<FeedClothes> result = feedMapper.toFeedClothes(
                UUID.randomUUID(),
                List.of()
        );

        assertThat(result).isEmpty();
    }
}