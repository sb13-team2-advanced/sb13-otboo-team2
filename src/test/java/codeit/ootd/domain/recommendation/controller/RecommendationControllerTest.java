package codeit.ootd.domain.recommendation.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;
import codeit.ootd.domain.recommendation.service.RecommendationService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증 사용자 UUID와 필수 날씨 파라미터의 API 계약을 검증한다.
 */
@WebMvcTest(value = RecommendationController.class, excludeFilters =
@org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = codeit.ootd.infra.storage.LocalStorageWebConfig.class))
class RecommendationControllerTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RecommendationService service;

    /**
     * 보안 필터에서 차단되어 MVC 요청으로 도달하지 않는 컨트롤러의 방어 로직을 검증한다.
     */
    @Test
    void 인증_정보가_없으면_인증_예외를_발생시킨다() {
        // given
        RecommendationController controller = new RecommendationController(service);
        UUID weatherId = UUID.randomUUID();

        // when then
        assertThatThrownBy(() -> controller.recommend(weatherId, null))
                .isInstanceOfSatisfying(OtbooException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
        verifyNoInteractions(service);
    }

    /**
     * 로그인 이름이 사용자 UUID가 아닌 경우 인증 실패로 응답하는지 검증한다.
     */
    @Test
    @WithMockUser(username = "invalid-user-id")
    void 인증_사용자_ID가_UUID가_아니면_실패한다() throws Exception {
        // given
        UUID weatherId = UUID.randomUUID();

        // when then
        mockMvc.perform(get("/api/recommendations").param("weatherId", weatherId.toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = USER_ID)
    void 인증_사용자와_날씨에_대한_추천을_반환한다() throws Exception {
        // given
        UUID weatherId = UUID.randomUUID();
        given(service.recommend(UUID.fromString(USER_ID), weatherId))
                .willReturn(new RecommendationResponse(weatherId, UUID.fromString(USER_ID), List.of()));
        // when then
        mockMvc.perform(get("/api/recommendations").param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.weatherId").value(weatherId.toString()))
                .andExpect(jsonPath("$.clothes").isEmpty());
    }

    @Test
    @WithMockUser(username = USER_ID)
    void 날씨_ID가_없거나_UUID가_아니면_실패한다() throws Exception {
        // given when then
        mockMvc.perform(get("/api/recommendations")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/recommendations").param("weatherId", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 비인증_사용자는_추천을_조회할_수_없다() throws Exception {
        // given when then
        mockMvc.perform(get("/api/recommendations").param("weatherId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
