package codeit.ootd.domain.notification.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.dto.response.NotificationDtoCursorResponse;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.service.NotificationService;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import codeit.ootd.global.exception.OtbooException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationControllerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new NotificationController(notificationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("첫 페이지와 다음 페이지 조회는 인증 사용자와 요청값을 전달하고 페이지를 반환한다")
    void getNotifications_passesAuthenticatedUserAndParameters(boolean withCursor) throws Exception {
        Instant createdAt = Instant.parse("2026-11-05T00:00:00Z");
        String cursor = withCursor ? createdAt.toString() : null;
        UUID idAfter = withCursor ? UUID.randomUUID() : null;
        UUID notificationId = UUID.randomUUID();
        var notification = new NotificationDto(
                notificationId, createdAt, userId, "알림", "내용", NotificationLevel.INFO);
        var response = new NotificationDtoCursorResponse(
                List.of(notification), createdAt.toString(), notificationId,
                true, 3L, "createdAt", SortDirection.DESCENDING);
        when(notificationService.getNotifications(userId, cursor, idAfter, 1)).thenReturn(response);
        var request = get("/api/notifications").param("limit", "1");
        if (withCursor) {
            request.param("cursor", cursor).param("idAfter", idAfter.toString());
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.data[0].receiverId").value(userId.toString()))
                .andExpect(jsonPath("$.nextCursor").value(createdAt.toString()))
                .andExpect(jsonPath("$.nextIdAfter").value(notificationId.toString()))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalCount").value(3));
        verify(notificationService).getNotifications(userId, cursor, idAfter, 1);
    }

    @Test
    @DisplayName("알림 목록 조회는 필수 limit이 없으면 400을 반환한다")
    void getNotifications_requiresLimit() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("idAfter가 UUID 형식이 아니면 서비스 호출 없이 400을 반환한다")
    void getNotifications_rejectsInvalidIdAfter() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .param("limit", "10")
                        .param("idAfter", "invalid-uuid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("읽음 처리는 알림 ID와 인증 사용자를 전달하고 본문 없이 204를 반환한다")
    void readNotifications_returnsNoContent() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(notificationService).readNotification(notificationId, userId);
    }

    @Test
    @DisplayName("읽을 수 없는 알림은 400을 반환한다")
    void readNotifications_returnsBadRequest() throws Exception {
        UUID notificationId = UUID.randomUUID();
        doThrow(new OtbooException(ErrorCode.INVALID_REQUEST))
                .when(notificationService).readNotification(notificationId, userId);

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.getMessage()));
    }
}
