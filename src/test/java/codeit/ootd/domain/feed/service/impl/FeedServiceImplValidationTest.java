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
import org.junit.jupiter.api.Test;
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

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID OTHER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

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
                () -> feedService.create(request, USER_ID)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FEED_CREATE_INVALID_REQUEST);

        verifyNoDatabaseAccess();
    }

    @Test
    @DisplayName("인증된 사용자 ID가 없으면 DB 조회 전에 거절한다")
    void rejectsMissingAuthenticatedUser() {
        FeedCreateRequest request = validRequest(USER_ID);

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.create(request, null)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);

        verifyNoDatabaseAccess();
    }

    @Test
    @DisplayName("Service 직접 호출에서도 작성자가 다르면 DB 조회 전에 거절한다")
    void rejectsDifferentAuthorWhenCalledDirectly() {
        FeedCreateRequest request = validRequest(OTHER_USER_ID);

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.create(request, USER_ID)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verifyNoDatabaseAccess();
    }

    private void verifyNoDatabaseAccess() {
        verifyNoInteractions(
                userRepository,
                weatherRepository,
                clothesRepository,
                feedRepository,
                feedClothesRepository
        );
    }

    private FeedCreateRequest validRequest(UUID authorId) {
        return new FeedCreateRequest(
                authorId,
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                "오늘의 코디"
        );
    }

    private static Stream<Arguments> invalidRequests() {
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
                                USER_ID, null, clothesIds, "오늘의 코디"
                        )
                ),
                Arguments.of(
                        "내용이 null인 경우",
                        new FeedCreateRequest(
                                USER_ID, weatherId, clothesIds, null
                        )
                ),
                Arguments.of(
                        "내용이 빈 문자열인 경우",
                        new FeedCreateRequest(
                                USER_ID, weatherId, clothesIds, ""
                        )
                ),
                Arguments.of(
                        "내용이 공백뿐인 경우",
                        new FeedCreateRequest(
                                USER_ID, weatherId, clothesIds, " \t\n "
                        )
                ),
                Arguments.of(
                        "의상 목록이 null인 경우",
                        new FeedCreateRequest(
                                USER_ID, weatherId, null, "오늘의 코디"
                        )
                ),
                Arguments.of(
                        "의상 목록에 null ID가 포함된 경우",
                        new FeedCreateRequest(
                                USER_ID,
                                weatherId,
                                Collections.singletonList(null),
                                "오늘의 코디"
                        )
                )
        );
    }
}