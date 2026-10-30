package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.dto.response.WeatherDto;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.mapper.WeatherDtoMapper;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 정상 등록 테스트")
class FeedServiceImplCreateTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WeatherRepository weatherRepository;

    @Mock
    private ClothesRepository clothesRepository;

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedClothesRepository feedClothesRepository;

    @Mock
    private WeatherDtoMapper weatherDtoMapper;

    @Mock
    private FeedResponseAssembler feedResponseAssembler;

    @Spy
    private FeedMapper feedMapper = Mappers.getMapper(FeedMapper.class);

    @InjectMocks
    private FeedServiceImpl feedService;

    @Captor
    private ArgumentCaptor<Feed> feedCaptor;

    @Captor
    private ArgumentCaptor<List<FeedClothes>> linksCaptor;

    @ParameterizedTest(name = "전날 날씨 존재 여부: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("날씨 정보를 저장하고 선택한 의상의 요청 순서를 유지해 응답한다")
    void createsFeed(boolean hasPreviousWeather) {
        UUID authorId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        UUID firstClothesId = UUID.randomUUID();
        UUID secondClothesId = UUID.randomUUID();
        LocalDate weatherDate = LocalDate.of(2026, 10, 30);

        User author = mock(User.class);
        Weather weather = mock(Weather.class);
        Weather previousWeather = hasPreviousWeather
                ? mock(Weather.class)
                : null;

        Clothes firstClothes = mock(Clothes.class);
        Clothes secondClothes = mock(Clothes.class);

        FeedCreateRequest request = new FeedCreateRequest(
                authorId,
                weatherId,
                List.of(firstClothesId, secondClothesId),
                "오늘의 코디"
        );

        Double temperatureDifference = hasPreviousWeather ? -1.25 : null;

        WeatherDto weatherDto = new WeatherDto(
                weatherId,
                null,
                "MOSTLY_CLOUDY",
                new WeatherDto.Temperature(
                        18.5, 12.0, 22.0, temperatureDifference
                ),
                new WeatherDto.Humidity((short) 65, null),
                new WeatherDto.Precipitation("RAIN", 2.4, 60.0),
                new WeatherDto.WindSpeed(3.0, "WEAK")
        );

        AuthorDto authorDto = new AuthorDto(authorId, "작성자", null);
        OotdDto firstOotd = new OotdDto(
                firstClothesId, "셔츠", null, "TOP", List.of()
        );
        OotdDto secondOotd = new OotdDto(
                secondClothesId, "바지", null, "BOTTOM", List.of()
        );

        when(userRepository.findById(authorId))
                .thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId))
                .thenReturn(Optional.of(weather));
        when(weather.getLocationId()).thenReturn(locationId);
        when(weather.getWeatherDate()).thenReturn(weatherDate);
        when(weatherRepository.findByLocationIdAndWeatherDate(
                locationId, weatherDate.minusDays(1)
        )).thenReturn(Optional.ofNullable(previousWeather));
        when(weatherDtoMapper.toDto(weather, previousWeather, null))
                .thenReturn(weatherDto);

        when(firstClothes.getId()).thenReturn(firstClothesId);
        when(secondClothes.getId()).thenReturn(secondClothesId);
        when(firstClothes.getUserId()).thenReturn(authorId);
        when(secondClothes.getUserId()).thenReturn(authorId);

        // DB 조회 결과의 순서는 요청 순서와 다를 수 있다.
        when(clothesRepository.findAllById(request.clothesIds()))
                .thenReturn(List.of(secondClothes, firstClothes));

        when(feedRepository.save(any(Feed.class)))
                .thenAnswer(invocation -> {
                    Feed feed = invocation.getArgument(0);
                    ReflectionTestUtils.setField(feed, "id", feedId);
                    return feed;
                });

        when(feedResponseAssembler.toAuthorDto(author))
                .thenReturn(authorDto);
        when(feedResponseAssembler.toOotdDto(firstClothes))
                .thenReturn(firstOotd);
        when(feedResponseAssembler.toOotdDto(secondClothes))
                .thenReturn(secondOotd);

        FeedDto result = feedService.create(request, request.authorId());

        verify(weatherDtoMapper).toDto(weather, previousWeather, null);
        verify(feedRepository).save(feedCaptor.capture());
        verify(feedClothesRepository).saveAll(linksCaptor.capture());

        Feed savedFeed = feedCaptor.getValue();

        assertThat(savedFeed.getAuthorId()).isEqualTo(authorId);
        assertThat(savedFeed.getWeatherId()).isEqualTo(weatherId);
        assertThat(savedFeed.getContent()).isEqualTo("오늘의 코디");
        assertThat(savedFeed.getWeatherSkyStatus()).isEqualTo("MOSTLY_CLOUDY");
        assertThat(savedFeed.getWeatherTemperatureCurrent()).isEqualTo(18.5);
        assertThat(savedFeed.getWeatherTemperatureMin()).isEqualTo(12.0);
        assertThat(savedFeed.getWeatherTemperatureMax()).isEqualTo(22.0);
        assertThat(savedFeed.getWeatherTemperatureComparedToDayBefore())
                .isEqualTo(temperatureDifference);
        assertThat(savedFeed.getWeatherPrecipitationType()).isEqualTo("RAIN");
        assertThat(savedFeed.getWeatherPrecipitationAmount()).isEqualTo(2.4);
        assertThat(savedFeed.getWeatherPrecipitationProbability()).isEqualTo(60.0);

        assertThat(linksCaptor.getValue())
                .extracting(
                        FeedClothes::getFeedId,
                        FeedClothes::getClothesId,
                        FeedClothes::getDisplayOrder
                )
                .containsExactly(
                        tuple(feedId, firstClothesId, 0),
                        tuple(feedId, secondClothesId, 1)
                );

        assertThat(result.id()).isEqualTo(feedId);
        assertThat(result.content()).isEqualTo("오늘의 코디");
        assertThat(result.author()).isEqualTo(authorDto);
        assertThat(result.ootds()).containsExactly(firstOotd, secondOotd);
        assertThat(result.weather().weatherId()).isEqualTo(weatherId);
        assertThat(result.weather().temperature().comparedToDayBefore())
                .isEqualTo(temperatureDifference);
        assertThat(result.likeCount()).isZero();
        assertThat(result.commentCount()).isZero();
        assertThat(result.likedByMe()).isFalse();
    }
}