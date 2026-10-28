package codeit.ootd.domain.follow.service;

import codeit.ootd.domain.follow.dto.response.FollowDto;
import codeit.ootd.domain.follow.dto.request.FollowCreateRequest;
import codeit.ootd.domain.follow.entity.Follow;
import codeit.ootd.domain.follow.exception.FollowAlreadyExistsException;
import codeit.ootd.domain.follow.repository.FollowRepository;
import codeit.ootd.domain.follow.service.FollowService;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.user.dto.response.UserSummary;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.OtbooException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import codeit.ootd.domain.follow.repository.FollowQueryRepository;
import static codeit.ootd.global.exception.ErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {
    @Mock FollowRepository follows;
    @Mock FollowQueryRepository queries;
    @Mock UserRepository users;
    @Mock ProfileRepository profiles;
    @InjectMocks FollowService service;
    private final UUID me = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    private User user(UUID id) {
        User user = User.create("name", "me@example.com", "encoded");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Follow follow() {
        Follow follow = new Follow(me, target);
        ReflectionTestUtils.setField(follow, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(follow, "createdAt", Instant.parse("2026-10-27T00:00:00Z"));
        return follow;
    }

    @Test
    @DisplayName("이메일로 현재 사용자의 ID를 조회한다")
    void currentUserId_returnsUserId() {
        when(users.findByEmail("me@example.com")).thenReturn(Optional.of(user(me)));
        assertEquals(me, service.currentUserId("me@example.com"));
    }

    @Test
    @DisplayName("인증 정보가 없으면 인증 필요 예외가 발생한다")
    void currentUserId_rejectsMissingAuthentication() {
        assertEquals(AUTHENTICATION_REQUIRED,
            assertThrows(OtbooException.class, () -> service.currentUserId(null)).getErrorCode());
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("팔로우 관계를 저장하고 두 사용자의 요약 정보를 반환한다")
    void create_savesRelationAndReturnsUserSummaries() {
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        when(users.findById(target)).thenReturn(Optional.of(user(target)));
        Follow saved = follow();
        Profile profile = new Profile(target);
        ReflectionTestUtils.setField(profile, "profileImageUrl", "https://example.com/profile.png");
        when(profiles.findByUserId(target)).thenReturn(Optional.of(profile));
        when(follows.saveAndFlush(any(Follow.class))).thenReturn(saved);
        var dto = service.create(new FollowCreateRequest(target, me), me);
        assertEquals(saved.getId(), dto.id());
        assertEquals(me, dto.follower().userId());
        assertEquals(target, dto.followee().userId());
        assertEquals("name", dto.followee().name());
        assertEquals("https://example.com/profile.png", dto.followee().profileImageUrl());
        assertNull(dto.follower().profileImageUrl());
        verify(follows).saveAndFlush(argThat(f -> me.equals(f.getFollowerId()) && target.equals(f.getFolloweeId())));
    }

    @Test
    @DisplayName("다른 사용자 명의로 팔로우를 생성하면 접근 거부 예외가 발생한다")
    void create_rejectsOtherFollower() {
        assertEquals(ACCESS_DENIED,
            assertThrows(OtbooException.class, () -> service.create(new FollowCreateRequest(target, me), target)).getErrorCode());
        verifyNoInteractions(follows, users);
    }

    @Test
    @DisplayName("이미 팔로우한 사용자를 다시 팔로우하면 중복 예외가 발생한다")
    void create_rejectsDuplicate() {
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        when(users.findById(target)).thenReturn(Optional.of(user(target)));
        when(follows.existsByFollowerIdAndFolloweeId(me, target)).thenReturn(true);
        assertThrows(FollowAlreadyExistsException.class, () -> service.create(new FollowCreateRequest(target, me), me));
        verify(follows, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("팔로워 및 팔로잉 수와 서로의 팔로우 여부 및 내 팔로우 ID를 반환한다")
    void getSummary_returnsCountsAndMyRelation() {
        when(users.findById(target)).thenReturn(Optional.of(user(target)));
        Follow mine = follow();
        when(follows.findByFollowerIdAndFolloweeId(me, target)).thenReturn(Optional.of(mine));
        when(follows.countByFolloweeId(target)).thenReturn(3L);
        when(follows.countByFollowerId(target)).thenReturn(2L);
        when(follows.existsByFollowerIdAndFolloweeId(target, me)).thenReturn(true);
        var dto = service.getSummary(target, me);
        assertEquals(3L, dto.followerCount());
        assertEquals(2L, dto.followingCount());
        assertTrue(dto.followedByMe());
        assertEquals(mine.getId(), dto.followedByMeId());
        assertTrue(dto.followingMe());
    }

    @Test
    @DisplayName("서로 팔로우하지 않으면 팔로우 여부는 false이고 내 팔로우 ID는 null이다")
    void getSummary_returnsNullRelationWhenNotFollowing() {
        when(users.findById(target)).thenReturn(Optional.of(user(target)));
        var dto = service.getSummary(target, me);
        assertFalse(dto.followedByMe());
        assertNull(dto.followedByMeId());
        assertFalse(dto.followingMe());
    }

    @Test
    @DisplayName("다음 페이지가 있으면 다음 커서와 검색 조건에 맞는 전체 개수를 반환한다")
    void getList_returnsNextCursorAndFilteredTotal() {
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        when(users.findById(target)).thenReturn(Optional.of(user(target)));
        Follow anchor = follow();
        var first = new FollowDto(anchor.getId(), new UserSummary(target, "name", null),
            new UserSummary(me, "name", null));
        when(queries.findList(me, true, null, null, 2, "name"))
            .thenReturn(List.of(anchor, follow()));
        when(queries.countList(me, true, "name")).thenReturn(5L);
        var dto = service.getList(me, true, null, null, 1, "name");
        assertEquals(List.of(first), dto.data());
        assertTrue(dto.hasNext());
        assertEquals(anchor.getId(), dto.nextIdAfter());
        assertEquals(anchor.getCreatedAt().toString(), dto.nextCursor());
        assertEquals(5L, dto.totalCount());
    }

    @Test
    @DisplayName("조회 결과가 비어 있으면 다음 페이지가 없고 다음 커서는 null이다")
    void getList_lastPageHasNoCursor() {
        when(users.findById(target)).thenReturn(Optional.of(user(target)));
        when(queries.findList(target, false, null, null, 11, null)).thenReturn(List.of());
        var dto = service.getList(target, false, null, null, 10, null);
        assertTrue(dto.data().isEmpty());
        assertFalse(dto.hasNext());
        assertNull(dto.nextCursor());
        assertNull(dto.nextIdAfter());
    }

    @Test
    @DisplayName("검색어와 커서는 목록 조회에 전달하고 전체 개수에는 검색어만 전달한다")
    void getList_passesSearchAndCursor() {
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        Instant cursor = Instant.parse("2026-10-27T00:00:00Z");
        UUID idAfter = UUID.randomUUID();
        String nameLike = "A%_!";
        when(queries.findList(me, true, cursor, idAfter, 11, nameLike))
            .thenReturn(List.of());
        service.getList(me, true, cursor.toString(), idAfter, 10, "A%_!");
        verify(queries).findList(me, true, cursor, idAfter, 11, nameLike);
        verify(queries).countList(me, true, nameLike);
    }

    @Test
    @DisplayName("팔로워 조회는 idAfter의 생성 시간을 커서로 사용한다")
    void getList_followersResolvesIdOnlyCursor() {
        when(users.findById(target)).thenReturn(Optional.of(user(target)));
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        Follow anchor = follow();
        when(follows.findById(anchor.getId())).thenReturn(Optional.of(anchor));
        when(queries.findList(target, false, anchor.getCreatedAt(), anchor.getId(), 11, null))
            .thenReturn(List.of(anchor));
        var dto = service.getList(target, false, null, anchor.getId(), 10, null);
        assertEquals(anchor.getId(), dto.data().get(0).id());
        assertEquals(target, dto.data().get(0).followee().userId());
        assertEquals(me, dto.data().get(0).follower().userId());
        assertFalse(dto.hasNext());
        verify(queries).countList(target, false, null);
    }

    @Test
    @DisplayName("커서의 형식이 잘못되면 유효하지 않은 커서 예외가 발생한다")
    void getList_rejectsInvalidCursor() {
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        assertEquals(FOLLOW_INVALID_CURSOR,
            assertThrows(OtbooException.class, () -> service.getList(me, true, "bad", null, 10, null)).getErrorCode());
        verifyNoInteractions(follows, queries);
    }

    @Test
    @DisplayName("조회 개수가 0이면 유효하지 않은 커서 예외가 발생한다")
    void getList_rejectsInvalidLimit() {
        assertEquals(FOLLOW_INVALID_CURSOR,
            assertThrows(OtbooException.class, () -> service.getList(me, true, null, null, 0, null)).getErrorCode());
        verifyNoInteractions(users, follows, queries);
    }

    @Test
    @DisplayName("본인이 생성한 팔로우 관계를 삭제한다")
    void cancel_deletesOwnedRelation() {
        Follow follow = follow();
        when(follows.findById(follow.getId())).thenReturn(Optional.of(follow));
        service.cancel(follow.getId(), me);
        verify(follows).delete(follow);
    }

    @Test
    @DisplayName("다른 사용자의 팔로우 관계를 취소하면 접근 거부 예외가 발생한다")
    void cancel_rejectsOtherOwner() {
        Follow follow = follow();
        when(follows.findById(follow.getId())).thenReturn(Optional.of(follow));
        assertEquals(ACCESS_DENIED,
            assertThrows(OtbooException.class, () -> service.cancel(follow.getId(), target)).getErrorCode());
        verify(follows, never()).delete(any());
    }

    @Test
    @DisplayName("존재하지 않는 팔로우를 취소하면 팔로우 없음 예외가 발생한다")
    void cancel_rejectsMissingRelation() {
        assertEquals(FOLLOW_NOT_FOUND,
            assertThrows(OtbooException.class, () -> service.cancel(UUID.randomUUID(), me)).getErrorCode());
        verify(follows, never()).delete(any());
    }
}

