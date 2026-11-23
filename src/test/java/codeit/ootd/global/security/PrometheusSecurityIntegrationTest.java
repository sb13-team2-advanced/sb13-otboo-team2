package codeit.ootd.global.security;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수집기의 익명 요청에 실제 JVM 메트릭을 반환하고 다른 관리 경로와 API는 보호하는지 검증한다.
 */
@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.prometheus.metrics.export.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PrometheusSecurityIntegrationTest {

    private final MockMvc mockMvc;

    @Test
    void 인증_없이_Prometheus_JVM_메트릭을_수집한다() throws Exception {
        // given
        String endpoint = "/actuator/prometheus";

        // when/then
        mockMvc.perform(get(endpoint))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    void 다른_관리_경로와_API는_인증을_요구한다() throws Exception {
        // given
        String healthEndpoint = "/actuator/health";
        String metricsEndpoint = "/actuator/metrics";
        String protectedEndpoint = "/api/profiles";

        // when/then
        mockMvc.perform(get(healthEndpoint)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(metricsEndpoint)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(protectedEndpoint)).andExpect(status().isUnauthorized());
    }
}
