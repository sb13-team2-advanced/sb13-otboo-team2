package codeit.ootd.domain.feed.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.dto.response.CommentDtoCursorResponse;
import codeit.ootd.domain.feed.service.FeedCommentService;
import codeit.ootd.global.enums.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("피드 댓글 목록 조회 Controller 테스트")
class FeedCommentControllerListTest {

    private final FeedCommentService service = mock(FeedCommentService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new FeedCommentController(service))
            .build();

    @Test
    @DisplayName("조회 개수가 없으면 서비스를 호출하지 않고 400을 반환한다")
    void rejectsRequestWithoutLimit() throws Exception {
        mockMvc.perform(get("/api/feeds/{feedId}/comments", UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("다음 페이지 요청의 커서를 서비스에 전달하고 댓글 목록을 200으로 반환한다")
    void returnsCommentsForRequestedCursor() throws Exception {
        UUID feedId = UUID.randomUUID();
        UUID idAfter = UUID.randomUUID();
        String cursor = "2026-11-10T06:00:00Z";

        CommentDto comment = new CommentDto(
                UUID.randomUUID(),
                Instant.parse("2026-11-10T05:59:00Z"),
                feedId,
                new AuthorDto(UUID.randomUUID(), "댓글 작성자", null),
                "오늘 코디 예뻐요!"
        );
        CommentDtoCursorResponse response = new CommentDtoCursorResponse(
                List.of(comment), null, null, false, 2L, "createdAt", SortDirection.DESCENDING
        );
        when(service.list(feedId, cursor, idAfter, 1)).thenReturn(response);

        mockMvc.perform(get("/api/feeds/{feedId}/comments", feedId)
                        .param("cursor", cursor)
                        .param("idAfter", idAfter.toString())
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(comment.id().toString()))
                .andExpect(jsonPath("$.data[0].content").value("오늘 코디 예뻐요!"))
                .andExpect(jsonPath("$.data[0].author.name").value("댓글 작성자"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(2));

        verify(service).list(feedId, cursor, idAfter, 1);
    }
}