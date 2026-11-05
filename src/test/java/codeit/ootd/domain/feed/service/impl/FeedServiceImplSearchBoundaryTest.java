package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.repository.FeedQueryRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
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
@DisplayName("피드 목록 조회 입력 경계 검증 테스트")
class FeedServiceImplSearchBoundaryTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private FeedQueryRepository feedQueryRepository;

    @Mock
    private FeedResponseAssembler feedResponseAssembler;

    @InjectMocks
    private FeedServiceImpl feedService;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("잘못된 조회 요청은 DB 조회 전에 400 오류로 거절한다")
    void rejectsInvalidRequestBeforeQuery(
            String description,
            FeedSearchRequest request
    ) {
        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.list(request, USER_ID)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(exception.getErrorCode().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(
                feedQueryRepository,
                feedResponseAssembler
        );
    }

    @Test
    @DisplayName("로그인 사용자 ID가 없으면 DB 조회 전에 401 오류로 거절한다")
    void rejectsMissingAuthenticatedUser() {
        FeedSearchRequest request = request(
                20,
                SortDirection.DESCENDING
        );

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.list(request, null)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
        assertThat(exception.getErrorCode().getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(
                feedQueryRepository,
                feedResponseAssembler
        );
    }

    @Test
    @DisplayName("좋아요 수가 음수인 커서는 DB 조회 전에 400 오류로 거절한다")
    void rejectsNegativeLikeCountCursor() {
        FeedSearchRequest request = new FeedSearchRequest(
                "-1",
                null,
                20,
                "likeCount",
                SortDirection.DESCENDING,
                null,
                null,
                null,
                null
        );

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.list(request, USER_ID)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FEED_INVALID_CURSOR);
        assertThat(exception.getErrorCode().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(
                feedQueryRepository,
                feedResponseAssembler
        );
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(
                        "요청 자체가 없는 경우",
                        (FeedSearchRequest) null
                ),
                Arguments.of(
                        "조회 개수가 없는 경우",
                        request(null, SortDirection.DESCENDING)
                ),
                Arguments.of(
                        "조회 개수가 0인 경우",
                        request(0, SortDirection.DESCENDING)
                ),
                Arguments.of(
                        "조회 개수가 음수인 경우",
                        request(-1, SortDirection.DESCENDING)
                ),
                Arguments.of(
                        "한 건 추가 조회 시 int 범위를 초과하는 경우",
                        request(Integer.MAX_VALUE, SortDirection.DESCENDING)
                ),
                Arguments.of(
                        "정렬 방향이 없는 경우",
                        request(20, null)
                )
        );
    }

    private static FeedSearchRequest request(
            Integer limit,
            SortDirection direction
    ) {
        return new FeedSearchRequest(
                null,
                null,
                limit,
                "createdAt",
                direction,
                null,
                null,
                null,
                null
        );
    }
}