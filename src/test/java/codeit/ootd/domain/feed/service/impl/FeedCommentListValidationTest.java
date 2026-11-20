package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 댓글 목록 조회 입력 검증 테스트")
class FeedCommentListValidationTest {

    @Mock
    private FeedCommentRepository comments;

    @InjectMocks
    private FeedCommentServiceImpl service;

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    @DisplayName("조회 개수가 유효하지 않으면 댓글을 조회하기 전에 거절한다")
    void rejectsInvalidLimit(int limit) {
        UUID feedId = UUID.randomUUID();

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> service.list(feedId, null, null, limit)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(comments);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCursors")
    @DisplayName("다음 페이지 커서가 불완전하거나 시각 형식이 잘못되면 거절한다")
    void rejectsInvalidCursor(String scenario, String cursor, UUID idAfter) {
        UUID feedId = UUID.randomUUID();

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> service.list(feedId, cursor, idAfter, 20)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEED_INVALID_CURSOR);
        verifyNoInteractions(comments);
    }

    private static Stream<Arguments> invalidCursors() {
        UUID commentId = UUID.randomUUID();
        return Stream.of(
                Arguments.of("시각만 보낸 경우", "2026-11-10T06:00:00Z", null),
                Arguments.of("댓글 ID만 보낸 경우", null, commentId),
                Arguments.of("시각을 해석할 수 없는 경우", "not-a-date", commentId)
        );
    }
}