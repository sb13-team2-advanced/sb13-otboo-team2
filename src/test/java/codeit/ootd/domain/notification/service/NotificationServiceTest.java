package codeit.ootd.domain.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.repository.NotificationQueryRepository;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 서비스 테스트")
class NotificationServiceTest {

    @Mock NotificationRepository repository;
    @Mock NotificationQueryRepository queries;
    @InjectMocks NotificationService service;

    private final UUID receiverId = UUID.randomUUID();
    private final Instant createdAt = Instant.parse("2026-11-05T00:00:00Z");

    @Test
    @DisplayName("조회 결과가 없으면 전체 개수는 0이고 다음 페이지 커서는 없다")
    void emptyPageHasNoNextCursor() {
        when(queries.findList(receiverId, null, null, 11)).thenReturn(List.of());

        var result = service.getNotifications(receiverId, null, null, 10);

        assertTrue(result.data().isEmpty());
        assertEquals(0L, result.totalCount());
        assertFalse(result.hasNext());
        assertNull(result.nextCursor());
        assertNull(result.nextIdAfter());
    }

    @Test
    @DisplayName("idAfter만 전달하면 본인 알림의 생성 시간을 조회해 커서로 사용한다")
    void resolvesIdOnlyCursor() {
        Notification anchor = notification();
        when(repository.findByIdAndReceiverId(anchor.getId(), receiverId)).thenReturn(Optional.of(anchor));
        when(queries.findList(receiverId, createdAt, anchor.getId(), 11)).thenReturn(List.of());

        service.getNotifications(receiverId, null, anchor.getId(), 10);

        verify(queries).findList(receiverId, createdAt, anchor.getId(), 11);
        verify(queries).countByReceiverId(receiverId);
    }

    @Test
    @DisplayName("idAfter만 전달했을 때 알림이 없거나 다른 수신자의 알림이면 거부한다")
    void rejectsMissingOrOtherReceiversAnchor() {
        UUID idAfter = UUID.randomUUID();
        when(repository.findByIdAndReceiverId(idAfter, receiverId)).thenReturn(Optional.empty());
        assertInvalid(() -> service.getNotifications(receiverId, null, idAfter, 10));
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("시간과 ID 커서를 함께 전달하면 기준 알림을 조회하지 않고 페이지를 조회한다")
    void usesCursorValuesWithoutLookingUpAnchor() {
        UUID idAfter = UUID.randomUUID();
        when(queries.findList(receiverId, createdAt, idAfter, 11)).thenReturn(List.of());

        service.getNotifications(receiverId, createdAt.toString(), idAfter, 10);

        verify(queries).findList(receiverId, createdAt, idAfter, 11);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "2026-11-05T00:00:00Z"})
    @DisplayName("시간 커서 형식이 잘못되거나 시간 커서만 전달하면 거부한다")
    void rejectsInvalidOrTimeOnlyCursor(String cursor) {
        assertInvalid(() -> service.getNotifications(receiverId, cursor, null, 10));
        verifyNoInteractions(repository, queries);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31})
    @DisplayName("조회 개수가 1~30 범위를 벗어나면 DB 조회 전에 거부한다")
    void rejectsInvalidLimit(int limit) {
        assertInvalid(() -> service.getNotifications(receiverId, null, null, limit));
        verifyNoInteractions(repository, queries);
    }

    @Test
    @DisplayName("인증 정보 없이 알림 목록을 조회하면 인증 필요 예외가 발생한다")
    void rejectsUnauthenticatedRequest() {
        var exception = assertThrows(OtbooException.class,
                () -> service.getNotifications(null, null, null, 10));
        assertEquals(ErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(repository, queries);
    }

    @Test
    @DisplayName("인증 정보 없이 알림 읽음 처리를 요청하면 인증 필요 예외가 발생한다")
    void readRejectsUnauthenticatedRequest() {
        var exception = assertThrows(OtbooException.class,
                () -> service.readNotification(UUID.randomUUID(), null));
        assertEquals(ErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(repository, queries);
    }

    private Notification notification() {
        Notification notification = new Notification(receiverId, "알림", "내용", NotificationLevel.INFO);
        ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        return notification;
    }

    private void assertInvalid(Executable action) {
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(OtbooException.class, action).getErrorCode());
    }
}
