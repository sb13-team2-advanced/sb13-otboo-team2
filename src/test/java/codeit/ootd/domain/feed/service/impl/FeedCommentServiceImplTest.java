package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 댓글 등록 서비스 테스트")
class FeedCommentServiceImplTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedCommentRepository feedCommentRepository;

    @InjectMocks
    private FeedCommentServiceImpl feedCommentService;

    @Test
    @DisplayName("로그인 사용자 ID가 없으면 인증 오류로 거절한다")
    void rejectsUnauthenticatedUser() {
        UUID feedId = UUID.randomUUID();
        CommentCreateRequest request =
                new CommentCreateRequest(feedId, UUID.randomUUID(), "잘 어울려요!");

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedCommentService.create(feedId, request, null)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
        verifyNoInteractions(feedRepository, feedCommentRepository);
    }

    @Test
    @DisplayName("요청의 작성자와 로그인 사용자가 다르면 댓글을 저장하지 않고 거절한다")
    void rejectsDifferentAuthor() {
        UUID feedId = UUID.randomUUID();
        UUID loginUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        CommentCreateRequest request =
                new CommentCreateRequest(feedId, otherUserId, "잘 어울려요!");

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedCommentService.create(feedId, request, loginUserId)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
        verifyNoInteractions(feedRepository, feedCommentRepository);
    }

    @Test
    @DisplayName("경로의 피드 ID와 요청 본문의 피드 ID가 다르면 거절한다")
    void rejectsMismatchedFeedId() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CommentCreateRequest request =
                new CommentCreateRequest(UUID.randomUUID(), userId, "잘 어울려요!");

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedCommentService.create(feedId, request, userId)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(feedRepository, feedCommentRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("댓글 내용이 없거나 공백뿐이면 거절한다")
    void rejectsBlankContent(String content) {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CommentCreateRequest request = new CommentCreateRequest(feedId, userId, content);

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedCommentService.create(feedId, request, userId)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(feedRepository, feedCommentRepository);
    }

    @Test
    @DisplayName("존재하지 않는 피드에는 댓글을 저장하지 않고 거절한다")
    void rejectsMissingFeed() {
        UUID feedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CommentCreateRequest request =
                new CommentCreateRequest(feedId, userId, "잘 어울려요!");
        when(feedRepository.findByIdForUpdate(feedId)).thenReturn(Optional.empty());

        OtbooException exception = assertThrows(
                OtbooException.class,
                () -> feedCommentService.create(feedId, request, userId)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEED_NOT_FOUND);
        verifyNoInteractions(feedCommentRepository);
    }
}