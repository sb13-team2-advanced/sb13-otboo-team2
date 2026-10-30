package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 등록 요청 검증 테스트")
class FeedServiceImplValidationTest {

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("잘못된 요청은 DB 조회 전에 거절한다")
    void rejectsInvalidRequestBeforeDatabaseAccess(
            String description,
            FeedCreateRequest request
    ) {
        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.create(request)
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

    private static Stream<Arguments> invalidRequests() {
        UUID authorId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        List<UUID> clothesIds = List.of(UUID.randomUUID());

        return Stream.of(
                Arguments.of(
                        "요청 자체가 없는 경우",
                        (FeedCreateRequest) null
                ),
                Arguments.of(
                        "작성자 ID가 없는 경우",
                        new FeedCreateRequest(
                                null, weatherId, clothesIds, "오늘의 코디"
                        )
                ),
                Arguments.of(
                        "날씨 ID가 없는 경우",
                        new FeedCreateRequest(
                                authorId, null, clothesIds, "오늘의 코디"
                        )
                ),
                Arguments.of(
                        "내용이 null인 경우",
                        new FeedCreateRequest(
                                authorId, weatherId, clothesIds, null
                        )
                ),
                Arguments.of(
                        "내용이 빈 문자열인 경우",
                        new FeedCreateRequest(
                                authorId, weatherId, clothesIds, ""
                        )
                ),
                Arguments.of(
                        "내용이 공백뿐인 경우",
                        new FeedCreateRequest(
                                authorId, weatherId, clothesIds, " \t\n "
                        )
                ),
                Arguments.of(
                        "의상 목록이 null인 경우",
                        new FeedCreateRequest(
                                authorId, weatherId, null, "오늘의 코디"
                        )
                ),
                Arguments.of(
                        "의상 목록에 null ID가 포함된 경우",
                        new FeedCreateRequest(
                                authorId,
                                weatherId,
                                Collections.singletonList(null),
                                "오늘의 코디"
                        )
                )
        );
    }
}