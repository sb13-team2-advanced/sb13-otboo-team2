package codeit.ootd.domain.directmessage.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DirectMessageControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DirectMessageController())
            .build();

    @Test
    @DisplayName("DM 목록 조회는 미구현 상태에서 501과 빈 본문을 반환한다")
    void getDms_returnsNotImplemented() throws Exception {
        mockMvc.perform(get("/api/direct-messages")
                        .param("userId", UUID.randomUUID().toString())
                        .param("limit", "10"))
                .andExpect(status().isNotImplemented())
                .andExpect(content().string(""));
    }
}
