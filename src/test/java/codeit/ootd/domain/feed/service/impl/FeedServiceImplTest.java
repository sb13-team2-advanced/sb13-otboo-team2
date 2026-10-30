package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 등록 서비스 테스트")
class FeedServiceImplTest {

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

    @InjectMocks
    private FeedServiceImpl feedService;

    @Test
    @DisplayName("작성자가 없으면 등록을 거절하고 데이터를 저장하지 않는다")
    void rejectsCreationWhenAuthorDoesNotExist() {
        UUID authorId = UUID.randomUUID();

        FeedCreateRequest request = new FeedCreateRequest(
                authorId,
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                "오늘의 코디"
        );

        when(userRepository.findById(authorId))
                .thenReturn(Optional.empty());

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.create(request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FEED_AUTHOR_NOT_FOUND);

        verify(userRepository).findById(authorId);
        verifyNoInteractions(
                weatherRepository,
                clothesRepository,
                feedRepository,
                feedClothesRepository
        );
    }

    @Test
    @DisplayName("날씨가 없으면 등록을 거절하고 데이터를 저장하지 않는다")
    void rejectsCreationWhenWeatherDoesNotExist() {
        UUID authorId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        User author = mock(User.class);

        FeedCreateRequest request = new FeedCreateRequest(
                authorId,
                weatherId,
                List.of(UUID.randomUUID()),
                "오늘의 코디"
        );

        when(userRepository.findById(authorId))
                .thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId))
                .thenReturn(Optional.empty());

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.create(request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FEED_WEATHER_NOT_FOUND);

        verify(userRepository).findById(authorId);
        verify(weatherRepository).findById(weatherId);
        verifyNoInteractions(
                clothesRepository,
                feedRepository,
                feedClothesRepository
        );
    }

    @Test
    @DisplayName("선택한 의상 중 하나라도 없으면 등록을 거절한다")
    void rejectsCreationWhenAnyClothesDoNotExist() {
        UUID authorId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID existingClothesId = UUID.randomUUID();
        UUID missingClothesId = UUID.randomUUID();

        User author = mock(User.class);
        Weather weather = mock(Weather.class);
        Clothes existingClothes = mock(Clothes.class);

        FeedCreateRequest request = new FeedCreateRequest(
                authorId,
                weatherId,
                List.of(existingClothesId, missingClothesId),
                "오늘의 코디"
        );

        when(userRepository.findById(authorId))
                .thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId))
                .thenReturn(Optional.of(weather));
        when(clothesRepository.findAllById(request.clothesIds()))
                .thenReturn(List.of(existingClothes));

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.create(request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FEED_CLOTHES_NOT_FOUND);

        verify(clothesRepository).findAllById(request.clothesIds());
        verifyNoInteractions(feedRepository, feedClothesRepository);
    }
}