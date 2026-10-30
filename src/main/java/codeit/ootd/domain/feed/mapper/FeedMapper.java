package codeit.ootd.domain.feed.mapper;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import codeit.ootd.domain.feed.dto.response.PrecipitationDto;
import codeit.ootd.domain.feed.dto.response.TemperatureDto;
import codeit.ootd.domain.feed.dto.response.WeatherSummaryDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FeedMapper {

    @Mapping(target = "authorId", source = "request.authorId")
    @Mapping(target = "weatherId", source = "request.weatherId")
    @Mapping(target = "content", source = "request.content")
    @Mapping(target = "likeCount", ignore = true)
    @Mapping(target = "commentCount", ignore = true)
    @Mapping(target = "weatherSkyStatus", source = "weather.skyStatus")
    @Mapping(target = "weatherTemperatureCurrent", source = "weather.temperature.current")
    @Mapping(
            target = "weatherTemperatureComparedToDayBefore",
            source = "weather.temperature.comparedToDayBefore"
    )
    @Mapping(target = "weatherTemperatureMin", source = "weather.temperature.min")
    @Mapping(target = "weatherTemperatureMax", source = "weather.temperature.max")
    @Mapping(target = "weatherPrecipitationType", source = "weather.precipitation.type")
    @Mapping(target = "weatherPrecipitationAmount", source = "weather.precipitation.amount")
    @Mapping(
            target = "weatherPrecipitationProbability",
            source = "weather.precipitation.probability"
    )
    Feed toEntity(FeedCreateRequest request, WeatherSummaryDto weather);

    default List<FeedClothes> toFeedClothes(
            UUID feedId,
            List<UUID> clothesIds
    ) {
        return IntStream.range(0, clothesIds.size())
                .mapToObj(index -> new FeedClothes(
                        feedId,
                        clothesIds.get(index),
                        index
                ))
                .toList();
    }

    @Mapping(target = "weather", source = "feed")
    @Mapping(target = "likedByMe", constant = "false")
    FeedDto toCreateResponse(Feed feed, AuthorDto author, List<OotdDto> ootds);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "name", source = "user.name")
    @Mapping(target = "profileImageUrl", source = "profileImageUrl")
    AuthorDto toAuthorDto(User user, String profileImageUrl);

    @Mapping(target = "weatherId", source = "id")
    WeatherSummaryDto toWeatherSummaryDto(WeatherDto weather);

    @Mapping(target = "skyStatus", source = "weatherSkyStatus")
    @Mapping(target = "precipitation", source = "feed")
    @Mapping(target = "temperature", source = "feed")
    WeatherSummaryDto toWeatherSummaryDto(Feed feed);

    @Mapping(target = "type", source = "weatherPrecipitationType")
    @Mapping(target = "amount", source = "weatherPrecipitationAmount")
    @Mapping(target = "probability", source = "weatherPrecipitationProbability")
    PrecipitationDto toPrecipitationDto(Feed feed);

    @Mapping(target = "current", source = "weatherTemperatureCurrent")
    @Mapping(target = "comparedToDayBefore", source = "weatherTemperatureComparedToDayBefore")
    @Mapping(target = "min", source = "weatherTemperatureMin")
    @Mapping(target = "max", source = "weatherTemperatureMax")
    TemperatureDto toTemperatureDto(Feed feed);
}