package codeit.ootd.domain.feed.service.impl;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import codeit.ootd.domain.feed.dto.response.WeatherSummaryDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.mapper.WeatherDtoMapper;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final UserRepository userRepository;
    private final WeatherRepository weatherRepository;
    private final ClothesRepository clothesRepository;
    private final FeedRepository feedRepository;
    private final FeedClothesRepository feedClothesRepository;
    private final WeatherDtoMapper weatherDtoMapper;
    private final FeedMapper feedMapper;
    private final FeedResponseAssembler feedResponseAssembler;

    @Override
    @Transactional
    public FeedDto create(FeedCreateRequest request, UUID userId) {
        if (userId == null) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        validateRequest(request);

        if (!userId.equals(request.authorId())) {
            throw new OtbooException(ErrorCode.ACCESS_DENIED);
        }

        User author = userRepository.findById(userId)
                .orElseThrow(
                        () -> new OtbooException(
                                ErrorCode.FEED_AUTHOR_NOT_FOUND
                        )
                );

        Weather weather = weatherRepository.findById(request.weatherId())
                .orElseThrow(
                        () -> new OtbooException(
                                ErrorCode.FEED_WEATHER_NOT_FOUND
                        )
                );

        List<Clothes> clothes =
                clothesRepository.findAllById(request.clothesIds());

        if (clothes.size() != request.clothesIds().size()) {
            throw new OtbooException(ErrorCode.FEED_CLOTHES_NOT_FOUND);
        }

        boolean containsOthersClothes = clothes.stream()
                .anyMatch(clothing ->
                        !userId.equals(clothing.getUserId())
                );

        if (containsOthersClothes) {
            throw new OtbooException(ErrorCode.ACCESS_DENIED);
        }

        Weather previousWeather = weatherRepository
                .findByLocationIdAndWeatherDate(
                        weather.getLocationId(),
                        weather.getWeatherDate().minusDays(1)
                )
                .orElse(null);

        WeatherDto weatherDto =
                weatherDtoMapper.toDto(weather, previousWeather, null);

        WeatherSummaryDto weatherSummary =
                feedMapper.toWeatherSummaryDto(weatherDto);

        Feed feed = feedMapper.toEntity(request, weatherSummary);
        Feed savedFeed = feedRepository.save(feed);

        List<FeedClothes> feedClothes = feedMapper.toFeedClothes(
                savedFeed.getId(),
                request.clothesIds()
        );

        feedClothesRepository.saveAll(feedClothes);

        AuthorDto authorDto =
                feedResponseAssembler.toAuthorDto(author);

        Map<UUID, Clothes> clothesById = clothes.stream()
                .collect(Collectors.toMap(
                        Clothes::getId,
                        Function.identity()
                ));

        List<OotdDto> ootds = request.clothesIds().stream()
                .map(clothesById::get)
                .map(feedResponseAssembler::toOotdDto)
                .toList();

        return feedMapper.toCreateResponse(savedFeed, authorDto, ootds);
    }

    private void validateRequest(FeedCreateRequest request) {
        if (request == null
                || request.authorId() == null
                || request.weatherId() == null
                || request.content() == null
                || request.content().isBlank()
                || request.clothesIds() == null
                || request.clothesIds().stream().anyMatch(Objects::isNull)) {
            throw new OtbooException(ErrorCode.FEED_CREATE_INVALID_REQUEST);
        }

        if (new HashSet<>(request.clothesIds()).size()
                != request.clothesIds().size()) {
            throw new OtbooException(ErrorCode.FEED_CREATE_INVALID_REQUEST);
        }
    }
}