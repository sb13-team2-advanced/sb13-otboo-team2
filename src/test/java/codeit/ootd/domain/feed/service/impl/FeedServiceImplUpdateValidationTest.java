package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.feed.dto.request.FeedUpdateRequest;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.Optional;
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
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 수정 요청 검증 테스트")
class FeedServiceImplUpdateValidationTest {

    private static final UUID FEED_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedResponseAssembler feedResponseAssembler;

    @InjectMocks
    private FeedServiceImpl feedService;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("잘못된 수정 요청은 DB 조회 전에 400 오류로 거절한다")
    void rejectsInvalidRequestBeforeQuery(
            String description,
            UUID feedId,
            FeedUpdateRequest request
    ) {
        assertThatThrownBy(() ->
                feedService.update(feedId, request, USER_ID)
        ).isInstanceOfSatisfying(
                OtbooException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST)
        );

        verifyNoInteractions(feedRepository, feedResponseAssembler);
    }

    @Test
    @DisplayName("로그인 사용자 ID가 없으면 DB 조회 전에 401 오류로 거절한다")
    void rejectsUnauthenticatedRequest() {
        FeedUpdateRequest request =
                new FeedUpdateRequest("수정할 내용");

        assertThatThrownBy(() ->
                feedService.update(FEED_ID, request, null)
        ).isInstanceOfSatisfying(
                OtbooException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED)
        );

        verifyNoInteractions(feedRepository, feedResponseAssembler);
    }

    @Test
    @DisplayName("존재하지 않는 피드는 404 오류로 거절한다")
    void rejectsMissingFeed() {
        given(feedRepository.findById(FEED_ID))
                .willReturn(Optional.empty());

        FeedUpdateRequest request =
                new FeedUpdateRequest("수정할 내용");

        assertThatThrownBy(() ->
                feedService.update(FEED_ID, request, USER_ID)
        ).isInstanceOfSatisfying(
                OtbooException.class,
                exception -> assertThat(
                        exception.getErrorCode().getStatus()
                ).isEqualTo(HttpStatus.NOT_FOUND)
        );

        verifyNoInteractions(feedResponseAssembler);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(
                        "피드 ID가 없는 경우",
                        null,
                        new FeedUpdateRequest("수정할 내용")
                ),
                Arguments.of(
                        "요청 자체가 없는 경우",
                        FEED_ID,
                        null
                ),
                Arguments.of(
                        "내용이 null인 경우",
                        FEED_ID,
                        new FeedUpdateRequest(null)
                ),
                Arguments.of(
                        "내용이 빈 문자열인 경우",
                        FEED_ID,
                        new FeedUpdateRequest("")
                ),
                Arguments.of(
                        "내용이 공백뿐인 경우",
                        FEED_ID,
                        new FeedUpdateRequest("   ")
                )
        );
    }
}