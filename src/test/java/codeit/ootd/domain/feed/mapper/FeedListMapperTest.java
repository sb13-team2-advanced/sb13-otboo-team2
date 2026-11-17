package codeit.ootd.domain.feed.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import codeit.ootd.domain.feed.dto.response.PrecipitationDto;
import codeit.ootd.domain.feed.dto.response.TemperatureDto;
import codeit.ootd.domain.feed.dto.response.WeatherSummaryDto;
import codeit.ootd.domain.feed.entity.Feed;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("피드 목록 응답 매핑 테스트")
class FeedListMapperTest {

    private final FeedMapper feedMapper =
            Mappers.getMapper(FeedMapper.class);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("피드 정보와 작성자·의상·날씨 및 내 좋아요 여부를 응답에 담는다")
    void mapsFeedToListResponse(boolean likedByMe) {
        UUID feedId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();

        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-11-04T00:10:00Z");

        Feed feed = Feed.builder()
                .authorId(authorId)
                .weatherId(weatherId)
                .content("오늘의 코디")
                .likeCount(3L)
                .commentCount(2)
                .weatherSkyStatus("CLOUDY")
                .weatherTemperatureCurrent(20.0)
                .weatherTemperatureComparedToDayBefore(null)
                .weatherTemperatureMin(15.0)
                .weatherTemperatureMax(25.0)
                .weatherPrecipitationType("RAIN")
                .weatherPrecipitationAmount(2.4)
                .weatherPrecipitationProbability(0.6)
                .build();

        ReflectionTestUtils.setField(feed, "id", feedId);
        ReflectionTestUtils.setField(feed, "createdAt", createdAt);
        ReflectionTestUtils.setField(feed, "updatedAt", updatedAt);

        AuthorDto author = new AuthorDto(
                authorId,
                "작성자",
                "/files/profiles/author.png"
        );

        OotdDto firstClothes = new OotdDto(
                UUID.randomUUID(),
                "셔츠",
                "/files/clothes/shirt.png",
                "TOP",
                List.of()
        );

        OotdDto secondClothes = new OotdDto(
                UUID.randomUUID(),
                "바지",
                "/files/clothes/pants.png",
                "BOTTOM",
                List.of()
        );

        List<OotdDto> ootds = List.of(firstClothes, secondClothes);

        FeedDto response = feedMapper.toListResponse(
                feed,
                author,
                ootds,
                likedByMe
        );

        assertThat(response.id()).isEqualTo(feedId);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        assertThat(response.content()).isEqualTo("오늘의 코디");
        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.commentCount()).isEqualTo(2);
        assertThat(response.likedByMe()).isEqualTo(likedByMe);

        assertThat(response.author()).isEqualTo(author);
        assertThat(response.ootds())
                .containsExactly(firstClothes, secondClothes);

        assertThat(response.weather()).isEqualTo(
                new WeatherSummaryDto(
                        weatherId,
                        "CLOUDY",
                        new PrecipitationDto("RAIN", 2.4, 0.6),
                        new TemperatureDto(20.0, null, 15.0, 25.0)
                )
        );
    }
}