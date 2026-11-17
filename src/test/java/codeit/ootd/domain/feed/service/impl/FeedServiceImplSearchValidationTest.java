package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 목록 조회 요청 검증 테스트")
class FeedServiceImplSearchValidationTest {

    @InjectMocks
    private FeedServiceImpl feedService;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unsupported"})
    @DisplayName("정렬 기준이 없거나 지원하지 않는 값이면 400 오류로 거절한다")
    void rejectsInvalidSortBy(String sortBy) {
        FeedSearchRequest request = request(sortBy, null);

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.list(request, UUID.randomUUID())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SORT_FIELD);
        assertThat(exception.getErrorCode().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCursors")
    @DisplayName("정렬 기준에 맞지 않는 커서는 400 오류로 거절한다")
    void rejectsInvalidCursor(
            String description,
            String sortBy,
            String cursor
    ) {
        FeedSearchRequest request = request(sortBy, cursor);

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedService.list(request, UUID.randomUUID())
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FEED_INVALID_CURSOR);
        assertThat(exception.getErrorCode().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static Stream<Arguments> invalidCursors() {
        return Stream.of(
                Arguments.of(
                        "생성 시각 커서가 날짜 형식이 아닌 경우",
                        "createdAt",
                        "not-a-date"
                ),
                Arguments.of(
                        "생성 시각 커서에 시각과 시간대가 없는 경우",
                        "createdAt",
                        "2026-11-04"
                ),
                Arguments.of(
                        "좋아요 커서가 숫자가 아닌 경우",
                        "likeCount",
                        "abc"
                ),
                Arguments.of(
                        "좋아요 커서가 정수가 아닌 경우",
                        "likeCount",
                        "1.5"
                ),
                Arguments.of(
                        "좋아요 커서가 long 범위를 초과하는 경우",
                        "likeCount",
                        "9223372036854775808"
                )
        );
    }

    private FeedSearchRequest request(String sortBy, String cursor) {
        return new FeedSearchRequest(
                cursor,
                null,
                20,
                sortBy,
                SortDirection.DESCENDING,
                null,
                null,
                null,
                null
        );
    }
}