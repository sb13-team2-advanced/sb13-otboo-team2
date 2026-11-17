package codeit.ootd.domain.feed.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.dto.response.FeedDtoCursorResponse;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.GlobalExceptionHandler;
import codeit.ootd.global.exception.OtbooException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("피드 목록 조회 Controller 테스트")
class FeedControllerListTest {

    @Mock
    private FeedService feedService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedController(feedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(
                                JsonMapper.builder().build()
                        )
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("검색 조건과 인증된 사용자 ID를 전달하고 목록 응답을 반환한다")
    void passesSearchConditionsAndAuthenticatedUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID idAfter = UUID.randomUUID();
        String cursor = "2026-11-05T00:00:00Z";

        authenticate(userId);

        FeedSearchRequest request = new FeedSearchRequest(
                cursor,
                idAfter,
                20,
                "createdAt",
                SortDirection.DESCENDING,
                "셔츠",
                "CLEAR",
                "NONE",
                authorId
        );

        when(feedService.list(request, userId))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/feeds")
                        .param("cursor", cursor)
                        .param("idAfter", idAfter.toString())
                        .param("limit", "20")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESCENDING")
                        .param("keywordLike", "셔츠")
                        .param("skyStatusEqual", "CLEAR")
                        .param("precipitationTypeEqual", "NONE")
                        .param("authorIdEqual", authorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

        verify(feedService).list(request, userId);
    }

    @Test
    @DisplayName("선택 검색 조건을 생략하면 null로 전달하고 목록을 반환한다")
    void allowsOmittedOptionalSearchConditions() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        FeedSearchRequest request = new FeedSearchRequest(
                null,
                null,
                20,
                "createdAt",
                SortDirection.DESCENDING,
                null,
                null,
                null,
                null
        );

        when(feedService.list(request, userId))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/feeds")
                        .param("limit", "20")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESCENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(0));

        verify(feedService).list(request, userId);
    }

    @Test
    @DisplayName("Service의 잘못된 커서 예외를 400 오류 응답으로 반환한다")
    void returnsBadRequestForInvalidCursor() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        FeedSearchRequest request = new FeedSearchRequest(
                "invalid-cursor",
                null,
                20,
                "createdAt",
                SortDirection.DESCENDING,
                null,
                null,
                null,
                null
        );

        when(feedService.list(request, userId)).thenThrow(
                new OtbooException(ErrorCode.FEED_INVALID_CURSOR)
        );

        mockMvc.perform(get("/api/feeds")
                        .param("cursor", "invalid-cursor")
                        .param("limit", "20")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESCENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("OtbooException"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.FEED_INVALID_CURSOR.getMessage()));

        verify(feedService).list(request, userId);
    }

    @ParameterizedTest(name = "{0}={1}이면 400으로 거절한다")
    @CsvSource({
            "limit, abc",
            "idAfter, invalid-uuid",
            "authorIdEqual, invalid-uuid",
            "sortDirection, INVALID"
    })
    @DisplayName("검색 조건의 타입이 잘못되면 Service 호출 전에 거절한다")
    void rejectsMalformedSearchParameters(
            String parameterName,
            String parameterValue
    ) throws Exception {
        authenticate(UUID.randomUUID());

        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.set("limit", "20");
        parameters.set("sortBy", "createdAt");
        parameters.set("sortDirection", "DESCENDING");
        parameters.set(parameterName, parameterValue);

        mockMvc.perform(get("/api/feeds").params(parameters))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(feedService);
    }

    private FeedDtoCursorResponse emptyResponse() {
        return new FeedDtoCursorResponse(
                List.of(),
                null,
                null,
                false,
                0L,
                "createdAt",
                SortDirection.DESCENDING
        );
    }

    private void authenticate(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                )
        );
    }
}