package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.request.FeedUpdateRequest;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 내용 최대 길이 검증 테스트")
class FeedServiceImplContentLimitTest {

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
    private FeedResponseAssembler feedResponseAssembler;

    @InjectMocks
    private FeedServiceImpl feedService;

    @Test
    @DisplayName("등록 내용이 1001자이면 DB 조회 전에 거절한다")
    void rejectsCreateContentOverLimit() {
        UUID userId = UUID.randomUUID();

        FeedCreateRequest request = new FeedCreateRequest(
                userId,
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                "가".repeat(1001)
        );

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.create(request, userId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FEED_CREATE_INVALID_REQUEST);

        verifyNoDatabaseAccess();
    }

    @Test
    @DisplayName("수정 내용이 1001자이면 DB 조회 전에 거절한다")
    void rejectsUpdateContentOverLimit() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        FeedUpdateRequest request =
                new FeedUpdateRequest("가".repeat(1001));

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.update(feedId, request, userId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verifyNoDatabaseAccess();
    }

    private void verifyNoDatabaseAccess() {
        verifyNoInteractions(
                userRepository,
                weatherRepository,
                clothesRepository,
                feedRepository,
                feedClothesRepository,
                feedResponseAssembler
        );
    }
}