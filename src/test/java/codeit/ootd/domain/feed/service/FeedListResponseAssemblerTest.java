package codeit.ootd.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.entity.FeedLike;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.mapper.OotdMapper;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 목록 응답 구성 테스트")
class FeedListResponseAssemblerTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private ClothesAttributeRepository clothesAttributeRepository;

    @Mock
    private ClothesAttributeDefinitionRepository clothesAttributeDefinitionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClothesRepository clothesRepository;

    @Mock
    private FeedClothesRepository feedClothesRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @Spy
    private FeedMapper feedMapper = Mappers.getMapper(FeedMapper.class);

    @Spy
    private OotdMapper ootdMapper = Mappers.getMapper(OotdMapper.class);

    @InjectMocks
    private FeedResponseAssembler assembler;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("작성자와 의상을 ID로 연결하고 표시 순서와 내 좋아요 여부를 반영한다")
    void assemblesFeedWithClothesInDisplayOrder(boolean likedByMe) {
        UUID viewerId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID shirtId = UUID.randomUUID();
        UUID pantsId = UUID.randomUUID();

        User author = User.create(
                "작성자",
                "author@example.com",
                "encoded-password"
        );
        ReflectionTestUtils.setField(author, "id", authorId);

        Feed feed = Feed.builder()
                .authorId(authorId)
                .weatherId(weatherId)
                .content("오늘의 코디")
                .likeCount(3L)
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .weatherTemperatureCurrent(20.0)
                .weatherTemperatureMin(15.0)
                .weatherTemperatureMax(25.0)
                .build();
        ReflectionTestUtils.setField(feed, "id", feedId);

        Clothes shirt = new Clothes(
                authorId, "셔츠", ClothesType.TOP, "/images/shirt.png"
        );
        ReflectionTestUtils.setField(shirt, "id", shirtId);

        Clothes pants = new Clothes(
                authorId, "바지", ClothesType.BOTTOM, "/images/pants.png"
        );
        ReflectionTestUtils.setField(pants, "id", pantsId);

        given(userRepository.findAllById(List.of(authorId)))
                .willReturn(List.of(author));
        given(profileRepository.findByUserId(authorId))
                .willReturn(Optional.empty());

        // 조회 순서와 관계없이 displayOrder에 따라 셔츠, 바지로 반환해야 한다.
        given(feedClothesRepository.findAllByFeedIdIn(List.of(feedId)))
                .willReturn(List.of(
                        new FeedClothes(feedId, pantsId, 1),
                        new FeedClothes(feedId, shirtId, 0)
                ));

        // findAllById의 반환 순서는 요청한 ID 순서와 다를 수 있다.
        given(clothesRepository.findAllById(
                org.mockito.ArgumentMatchers.<Iterable<UUID>>any()
        )).willReturn(List.of(pants, shirt));

        given(clothesAttributeRepository.findAllByClothesId(shirtId))
                .willReturn(List.of());
        given(clothesAttributeRepository.findAllByClothesId(pantsId))
                .willReturn(List.of());

        List<FeedLike> likes = likedByMe
                ? List.of(new FeedLike(feedId, viewerId))
                : List.of();

        given(feedLikeRepository.findAllByUserIdAndFeedIdIn(
                viewerId, List.of(feedId)
        )).willReturn(likes);

        List<FeedDto> result =
                assembler.toFeedDtos(List.of(feed), viewerId);

        assertThat(result).hasSize(1);

        FeedDto response = result.get(0);

        assertThat(response.id()).isEqualTo(feedId);
        assertThat(response.content()).isEqualTo("오늘의 코디");
        assertThat(response.author()).isEqualTo(
                new AuthorDto(authorId, "작성자", null)
        );
        assertThat(response.ootds()).containsExactly(
                new OotdDto(
                        shirtId,
                        "셔츠",
                        "/images/shirt.png",
                        "TOP",
                        List.of()
                ),
                new OotdDto(
                        pantsId,
                        "바지",
                        "/images/pants.png",
                        "BOTTOM",
                        List.of()
                )
        );
        assertThat(response.likedByMe()).isEqualTo(likedByMe);
        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.weather().weatherId()).isEqualTo(weatherId);
        assertThat(response.weather().temperature().current())
                .isEqualTo(20.0);
        assertThat(response.weather().temperature().comparedToDayBefore())
                .isNull();
    }
}