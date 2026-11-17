package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
@DisplayName("피드 등록 의상 검증 테스트")
class FeedServiceImplClothesValidationTest {

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
    @DisplayName("의상 ID가 중복되면 DB 조회 전에 잘못된 요청으로 거절한다")
    void rejectsDuplicateClothesIdsBeforeDatabaseAccess() {
        UUID clothesId = UUID.randomUUID();

        FeedCreateRequest request = new FeedCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(clothesId, clothesId),
                "오늘의 코디"
        );

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.create(request, request.authorId())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FEED_CREATE_INVALID_REQUEST);

        verifyNoInteractions(
                userRepository,
                weatherRepository,
                clothesRepository,
                feedRepository,
                feedClothesRepository
        );
    }

    @Test
    @DisplayName("다른 사용자의 의상이 하나라도 포함되면 저장 전에 거절한다")
    void rejectsClothesOwnedByAnotherUser() {
        UUID authorId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID ownClothesId = UUID.randomUUID();
        UUID otherClothesId = UUID.randomUUID();

        User author = mock(User.class);
        Weather weather = mock(Weather.class);
        Clothes ownClothes = mock(Clothes.class);
        Clothes otherClothes = mock(Clothes.class);

        FeedCreateRequest request = new FeedCreateRequest(
                authorId,
                weatherId,
                List.of(ownClothesId, otherClothesId),
                "오늘의 코디"
        );

        when(userRepository.findById(authorId))
                .thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId))
                .thenReturn(Optional.of(weather));
        when(clothesRepository.findAllById(request.clothesIds()))
                .thenReturn(List.of(ownClothes, otherClothes));
        when(ownClothes.getUserId()).thenReturn(authorId);
        when(otherClothes.getUserId()).thenReturn(UUID.randomUUID());

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.create(request, request.authorId())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(weather, never()).getLocationId();
        verifyNoInteractions(feedRepository, feedClothesRepository);
    }
}