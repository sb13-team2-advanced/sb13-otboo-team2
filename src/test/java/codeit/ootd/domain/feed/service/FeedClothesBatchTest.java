package codeit.ootd.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.mapper.OotdMapper;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("피드 목록 의상 속성 일괄 조회 테스트")
class FeedClothesBatchTest {

    private final ProfileRepository profileRepository =
            mock(ProfileRepository.class);
    private final ClothesAttributeRepository attributeRepository =
            mock(ClothesAttributeRepository.class);
    private final ClothesAttributeDefinitionRepository definitionRepository =
            mock(ClothesAttributeDefinitionRepository.class);
    private final UserRepository userRepository =
            mock(UserRepository.class);
    private final ClothesRepository clothesRepository =
            mock(ClothesRepository.class);
    private final FeedClothesRepository feedClothesRepository =
            mock(FeedClothesRepository.class);
    private final FeedLikeRepository feedLikeRepository =
            mock(FeedLikeRepository.class);

    private final FeedResponseAssembler assembler = new FeedResponseAssembler(
            profileRepository,
            Mappers.getMapper(FeedMapper.class),
            attributeRepository,
            definitionRepository,
            Mappers.getMapper(OotdMapper.class),
            userRepository,
            clothesRepository,
            feedClothesRepository,
            feedLikeRepository
    );

    @Test
    @DisplayName("여러 의상의 속성을 일괄 조회하고 의상 순서와 속성 연결을 유지한다")
    void batchesAttributesAndPreservesClothesOrder() {
        UUID authorId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        UUID topId = UUID.randomUUID();
        UUID bottomId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();

        User author = mock(User.class);
        when(author.getId()).thenReturn(authorId);
        when(author.getName()).thenReturn("작성자");

        Feed feed = Feed.builder()
                .content("오늘의 옷")
                .authorId(authorId)
                .weatherId(UUID.randomUUID())
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .build();
        ReflectionTestUtils.setField(feed, "id", feedId);

        Clothes top = new Clothes(
                authorId, "흰색 셔츠", ClothesType.TOP, "/top.png"
        );
        Clothes bottom = new Clothes(
                authorId, "검정 바지", ClothesType.BOTTOM, "/bottom.png"
        );
        ReflectionTestUtils.setField(top, "id", topId);
        ReflectionTestUtils.setField(bottom, "id", bottomId);

        ClothesAttribute topColor =
                new ClothesAttribute(topId, colorId, "흰색");
        ClothesAttribute bottomColor =
                new ClothesAttribute(bottomId, colorId, "검정");

        ClothesAttributeDefinition color = new ClothesAttributeDefinition(
                "색상", List.of("흰색", "검정")
        );
        ReflectionTestUtils.setField(color, "id", colorId);

        when(userRepository.findAllById(List.of(authorId)))
                .thenReturn(List.of(author));

        // 저장소의 반환 순서와 화면의 표시 순서를 다르게 준비한다.
        when(feedClothesRepository.findAllByFeedIdIn(List.of(feedId)))
                .thenReturn(List.of(
                        new FeedClothes(feedId, bottomId, 1),
                        new FeedClothes(feedId, topId, 0)
                ));
        when(clothesRepository.findAllById(anyIterable()))
                .thenReturn(List.of(bottom, top));

        when(attributeRepository.findAllByClothesIdIn(anyCollection()))
                .thenReturn(List.of(bottomColor, topColor));
        when(definitionRepository.findAllById(anyIterable()))
                .thenReturn(List.of(color));

        var result = assembler.toFeedDtos(List.of(feed), authorId);

        // 여러 옷이어도 속성 및 속성 정의는 각각 한 번만 조회한다.
        var clothesIdsCaptor =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(attributeRepository)
                .findAllByClothesIdIn(clothesIdsCaptor.capture());
        assertThat(clothesIdsCaptor.getValue())
                .containsExactlyInAnyOrder(topId, bottomId);

        verify(attributeRepository, never()).findAllByClothesId(any());
        verify(definitionRepository).findAllById(List.of(colorId));

        assertThat(result).hasSize(1);
        var ootds = result.get(0).ootds();
        assertThat(ootds)
                .extracting(ootd -> ootd.clothesId())
                .containsExactly(topId, bottomId);

        assertThat(ootds.get(0).attributes()).containsExactly(
                new ClothesAttributeWithDefDto(
                        colorId, "색상", List.of("흰색", "검정"), "흰색"
                )
        );
        assertThat(ootds.get(1).attributes()).containsExactly(
                new ClothesAttributeWithDefDto(
                        colorId, "색상", List.of("흰색", "검정"), "검정"
                )
        );
    }
}