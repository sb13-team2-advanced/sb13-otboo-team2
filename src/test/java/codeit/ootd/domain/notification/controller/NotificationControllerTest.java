package codeit.ootd.domain.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new NotificationController())
            .build();

    @Test
    @DisplayName("알림 목록 조회는 미구현 상태에서 501과 빈 본문을 반환한다")
    void getNotifications_returnsNotImplemented() throws Exception {
        mockMvc.perform(get("/api/notifications").param("limit", "10"))
                .andExpect(status().isNotImplemented())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("알림 읽음 처리는 미구현 상태에서 501과 빈 본문을 반환한다")
    void readNotifications_returnsNotImplemented() throws Exception {
        mockMvc.perform(delete("/api/notifications/{notificationId}", UUID.randomUUID()))
                .andExpect(status().isNotImplemented())
                .andExpect(content().string(""));
    }
}
