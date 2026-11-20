package codeit.ootd.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class ActuatorMetricsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("개발 환경에서는 인증 없이 Actuator 메트릭을 조회할 수 있다")
    void metricsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    @DisplayName("개발 환경에서도 일반 API는 인증이 필요하다")
    void protectedApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/profiles"))
            .andExpect(status().isUnauthorized());
    }
}
