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
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("피드 응답 매핑 테스트")
class FeedMapperTest {

    private final FeedMapper feedMapper =
            Mappers.getMapper(FeedMapper.class);

    @Test
    @DisplayName("전날 대비 기온이 없으면 응답에도 null을 유지한다")
    void preservesNullWhenTemperatureComparisonIsMissing() {
        Feed feed = feedBuilder().build();

        TemperatureDto result = feedMapper.toTemperatureDto(feed);

        assertThat(result.comparedToDayBefore()).isNull();
        assertThat(result.current()).isEqualTo(20.0);
        assertThat(result.min()).isEqualTo(15.0);
        assertThat(result.max()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("전날과 기온이 같으면 null이 아닌 0을 반환한다")
    void preservesZeroWhenTemperatureIsUnchanged() {
        Feed feed = feedBuilder()
                .weatherTemperatureComparedToDayBefore(0.0)
                .build();

        TemperatureDto result = feedMapper.toTemperatureDto(feed);

        assertThat(result.comparedToDayBefore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("등록 응답에 피드 정보와 작성자·날씨·의상 목록을 포함한다")
    void createsFeedResponseWithAllRequiredInformation() {
        UUID feedId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-29T00:00:00Z");

        Feed feed = feedBuilder()
                .authorId(authorId)
                .weatherId(weatherId)
                .build();

        ReflectionTestUtils.setField(feed, "id", feedId);
        ReflectionTestUtils.setField(feed, "createdAt", createdAt);
        ReflectionTestUtils.setField(feed, "updatedAt", createdAt);

        AuthorDto author = new AuthorDto(
                authorId,
                "작성자",
                "/images/profile.png"
        );

        OotdDto top = new OotdDto(
                UUID.randomUUID(),
                "흰색 티셔츠",
                "/images/tshirt.png",
                "TOP",
                List.of()
        );

        OotdDto bottom = new OotdDto(
                UUID.randomUUID(),
                "청바지",
                "/images/jeans.png",
                "BOTTOM",
                List.of()
        );

        List<OotdDto> ootds = List.of(top, bottom);

        FeedDto result = feedMapper.toCreateResponse(
                feed,
                author,
                ootds
        );

        assertThat(result.id()).isEqualTo(feedId);
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.updatedAt()).isEqualTo(createdAt);
        assertThat(result.content()).isEqualTo("오늘의 코디");
        assertThat(result.author()).isEqualTo(author);
        assertThat(result.ootds()).containsExactly(top, bottom);

        assertThat(result.weather()).isEqualTo(
                new WeatherSummaryDto(
                        weatherId,
                        "CLEAR",
                        new PrecipitationDto("NONE", 0.0, 0.0),
                        new TemperatureDto(20.0, null, 15.0, 25.0)
                )
        );

        assertThat(result.likeCount()).isZero();
        assertThat(result.commentCount()).isZero();
        assertThat(result.likedByMe()).isFalse();
    }

    private Feed.FeedBuilder feedBuilder() {
        return Feed.builder()
                .authorId(UUID.randomUUID())
                .weatherId(UUID.randomUUID())
                .content("오늘의 코디")
                .weatherTemperatureCurrent(20.0)
                .weatherTemperatureMin(15.0)
                .weatherTemperatureMax(25.0)
                .weatherSkyStatus("CLEAR")
                .weatherPrecipitationType("NONE")
                .weatherPrecipitationAmount(0.0)
                .weatherPrecipitationProbability(0.0);
    }
}