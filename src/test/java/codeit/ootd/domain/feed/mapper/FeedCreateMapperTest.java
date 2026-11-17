package codeit.ootd.domain.feed.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.PrecipitationDto;
import codeit.ootd.domain.feed.dto.response.TemperatureDto;
import codeit.ootd.domain.feed.dto.response.WeatherSummaryDto;
import codeit.ootd.domain.feed.entity.Feed;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

@DisplayName("피드 등록 엔티티 매핑 테스트")
class FeedCreateMapperTest {

    private final FeedMapper feedMapper =
            Mappers.getMapper(FeedMapper.class);

    @Test
    @DisplayName("등록 요청과 날씨 정보를 저장할 피드 엔티티로 변환한다")
    void createsFeedFromRequestAndWeather() {
        FeedCreateRequest request = createRequest();
        WeatherSummaryDto weather = createWeather(
                request.weatherId(),
                1.5
        );

        Feed result = feedMapper.toEntity(request, weather);

        assertThat(result.getAuthorId()).isEqualTo(request.authorId());
        assertThat(result.getWeatherId()).isEqualTo(request.weatherId());
        assertThat(result.getContent()).isEqualTo(request.content());

        assertThat(result.getWeatherSkyStatus()).isEqualTo("CLOUDY");
        assertThat(result.getWeatherTemperatureCurrent()).isEqualTo(20.0);
        assertThat(result.getWeatherTemperatureComparedToDayBefore())
                .isEqualTo(1.5);
        assertThat(result.getWeatherTemperatureMin()).isEqualTo(15.0);
        assertThat(result.getWeatherTemperatureMax()).isEqualTo(25.0);

        assertThat(result.getWeatherPrecipitationType()).isEqualTo("RAIN");
        assertThat(result.getWeatherPrecipitationAmount()).isEqualTo(2.4);
        assertThat(result.getWeatherPrecipitationProbability()).isEqualTo(0.6);

        assertThat(result.getLikeCount()).isZero();
        assertThat(result.getCommentCount()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(doubles = {0.0, -1.5, 2.5})
    @DisplayName("전날 대비 기온의 null·0·음수·양수를 그대로 유지한다")
    void preservesTemperatureComparison(Double comparison) {
        FeedCreateRequest request = createRequest();
        WeatherSummaryDto weather = createWeather(
                request.weatherId(),
                comparison
        );

        Feed result = feedMapper.toEntity(request, weather);

        assertThat(result.getWeatherTemperatureComparedToDayBefore())
                .isEqualTo(comparison);
    }

    private FeedCreateRequest createRequest() {
        return new FeedCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                "오늘의 코디"
        );
    }

    private WeatherSummaryDto createWeather(
            UUID weatherId,
            Double comparison
    ) {
        return new WeatherSummaryDto(
                weatherId,
                "CLOUDY",
                new PrecipitationDto("RAIN", 2.4, 0.6),
                new TemperatureDto(20.0, comparison, 15.0, 25.0)
        );
    }
}