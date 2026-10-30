package codeit.ootd.domain.follow.controller;

import codeit.ootd.domain.follow.controller.FollowController;
import codeit.ootd.domain.follow.dto.response.*;
import codeit.ootd.domain.follow.dto.request.FollowCreateRequest;
import codeit.ootd.domain.follow.service.FollowService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowControllerTest {
    @Mock FollowService service;
    @InjectMocks FollowController controller;
    private final UUID me = UUID.randomUUID();
    private final Principal principal = new UsernamePasswordAuthenticationToken(me, null, List.of());

    @Test
    @DisplayName("팔로우 생성 시 201 상태 코드와 팔로우 정보를 반환한다")
    void createFollow_returnsCreated() {
        var request = new FollowCreateRequest(UUID.randomUUID(), me);
        var dto = new FollowDto(UUID.randomUUID(), null, null);
        when(service.currentUserId(principal.getName())).thenReturn(me);
        when(service.create(request, me)).thenReturn(dto);
        var response = controller.createFollow(request, principal);
        assertEquals(201, response.getStatusCode().value());
        assertSame(dto, response.getBody());
        verify(service).currentUserId(me.toString());
    }

    @Test
    @DisplayName("팔로우 요약 조회 시 200 상태 코드와 요약 정보를 반환한다")
    void getFollowSummary_returnsSummary() {
        UUID target = UUID.randomUUID();
        var dto = new FollowSummaryDto(target, 1L, 2L, false, null, true);
        when(service.currentUserId(principal.getName())).thenReturn(me);
        when(service.getSummary(target, me)).thenReturn(dto);
        var response = controller.getFollowSummary(target, principal);
        assertEquals(200, response.getStatusCode().value());
        assertSame(dto, response.getBody());
        verify(service).currentUserId(me.toString());
    }

    @Test
    @DisplayName("팔로잉 목록 조회 조건을 서비스에 전달하고 결과를 반환한다")
    void getFollowings_passesParameters() {
        var dto = new FollowListResponse(List.of(), null, null, false, 0L, null, null);
        UUID idAfter = UUID.randomUUID();
        when(service.getList(me, true, "cursor", idAfter, 10, "name")).thenReturn(dto);
        var response = controller.getFollowings(me, "cursor", idAfter, 10, "name");
        assertEquals(200, response.getStatusCode().value());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("팔로워 목록 조회 조건을 서비스에 전달하고 결과를 반환한다")
    void getFollowers_passesParameters() {
        var dto = new FollowListResponse(List.of(), null, null, false, 0L, null, null);
        when(service.getList(me, false, null, null, 10, null)).thenReturn(dto);
        var response = controller.getFollowers(me, null, null, 10, null);
        assertEquals(200, response.getStatusCode().value());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("팔로우 취소를 서비스에 요청하고 204 상태 코드를 반환한다")
    void cancelFollow_returnsNoContent() {
        UUID id = UUID.randomUUID();
        when(service.currentUserId(principal.getName())).thenReturn(me);
        var response = controller.cancelFollow(id, principal);
        assertEquals(204, response.getStatusCode().value());
        verify(service).cancel(id, me);
        verify(service).currentUserId(me.toString());
    }
}
