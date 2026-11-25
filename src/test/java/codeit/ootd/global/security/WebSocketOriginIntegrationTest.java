package codeit.ootd.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "otboo.websocket.allowed-origins=https://d32h6socf0ryd3.cloudfront.net")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSocketOriginIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("프록시 내부 주소와 다른 배포 Origin도 SockJS 전송에 접근한다")
    void allowsDeploymentOriginBehindProxy() throws Exception {
        // 없는 세션의 404는 Origin 검사와 HTTP 인증·CSRF 검사를 통과했다는 뜻이다.
        mockMvc.perform(post("/ws/000/origin-test/xhr_send")
                        .header("Origin", "https://d32h6socf0ryd3.cloudfront.net")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("허용하지 않은 외부 Origin의 SockJS 전송은 차단한다")
    void rejectsUntrustedOrigin() throws Exception {
        mockMvc.perform(post("/ws/000/origin-test/xhr_send")
                        .header("Origin", "https://untrusted.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());
    }
}
