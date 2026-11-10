package codeit.ootd.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import codeit.ootd.domain.follow.exception.FollowAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 중복 팔로우 예외의 오류 코드와 HTTP 응답 변환 및 잘못된 오류 코드 거부를 검증한다.
 */
class GlobalExceptionHandlerTest {

    @Test
    void 중복_팔로우_예외에_오류_코드와_메시지가_설정된다() {
        // given
        ErrorCode errorCode = ErrorCode.FOLLOW_ALREADY_EXISTS;

        // when
        FollowAlreadyExistsException exception = new FollowAlreadyExistsException();

        // then
        assertThat(exception.getErrorCode()).isSameAs(errorCode);
        assertThat(exception).hasMessage("이미 팔로우한 사용자입니다.");
    }

    @Test
    void 중복_팔로우_예외를_잘못된_요청_응답으로_변환한다() {
        // given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        FollowAlreadyExistsException exception = new FollowAlreadyExistsException();

        // when
        ResponseEntity<ErrorResponse> response = handler.handleOtbooException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exceptionName()).isEqualTo("FollowAlreadyExistsException");
        assertThat(response.getBody().message()).isEqualTo("이미 팔로우한 사용자입니다.");
        assertThat(response.getBody().details()).isEmpty();
    }

    @Test
    void 오류_코드가_없으면_예외_생성을_거부한다() {
        // given
        ErrorCode errorCode = null;

        // when/then
        assertThatNullPointerException().isThrownBy(() -> new OtbooException(errorCode));
    }
}
