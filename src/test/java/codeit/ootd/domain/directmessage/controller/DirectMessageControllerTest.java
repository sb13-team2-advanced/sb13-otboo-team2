package codeit.ootd.domain.directmessage.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.directmessage.dto.DirectMessageDtoCursorResponse;
import codeit.ootd.domain.directmessage.service.DirectMessageService;
import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("DM 목록 조회 컨트롤러")
class DirectMessageControllerTest {

    private final DirectMessageService service = mock(DirectMessageService.class);
    private final UUID currentUserId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DirectMessageController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUserId, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 사용자와 커서를 서비스에 전달하고 목록 응답을 반환한다")
    void passesAuthenticatedUserAndCursorToService() throws Exception {
        String cursor = "2026-11-16T00:00:00Z";
        UUID idAfter = UUID.randomUUID();
        when(service.getMessages(currentUserId, otherUserId, cursor, idAfter, 20))
                .thenReturn(new DirectMessageDtoCursorResponse(
                        List.of(), null, null, false, 0L, "createdAt", SortDirection.DESCENDING));
        mockMvc.perform(get("/api/direct-messages")
                        .param("userId", otherUserId.toString())
                        .param("cursor", cursor).param("idAfter", idAfter.toString())
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
        verify(service).getMessages(currentUserId, otherUserId, cursor, idAfter, 20);
    }

}
