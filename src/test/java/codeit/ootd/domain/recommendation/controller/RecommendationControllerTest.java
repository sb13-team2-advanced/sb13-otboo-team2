package codeit.ootd.domain.recommendation.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;
import codeit.ootd.domain.recommendation.service.RecommendationService;

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
