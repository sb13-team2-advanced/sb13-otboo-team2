package codeit.ootd.global.security;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Actuator 준비 상태 조회의 익명 접근과 상태 코드, 다른 관리 경로의 인증 경계를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ReadinessHealthIntegrationTest {

    private final MockMvc mockMvc;
    private final ApplicationContext applicationContext;

    @Test
    void 인증_없이_준비_상태를_조회하면_상세_정보_없이_정상을_반환한다() throws Exception {
        // given: 애플리케이션과 테스트 데이터베이스가 준비되어 있다.

        // when/then
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void 요청을_받을_준비가_안_되면_서비스_사용_불가를_반환한다() throws Exception {
        // given
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);

        try {
            // when/then
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
        } finally {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    @Test
    void 전체_헬스체크는_인증_없이_조회할_수_없다() throws Exception {
        // given: 인증 정보가 없다.

        // when/then
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized());
    }
}
