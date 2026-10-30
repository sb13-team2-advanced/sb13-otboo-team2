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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    @DisplayName("principal의 UUID를 현재 사용자 ID로 사용한다")
    void currentUserId_returnsUserId() {
        assertEquals(me, service.currentUserId(me.toString()));
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"me@example.com", "", "invalid-uuid"})
    @DisplayName("principal이 UUID 형식이 아니면 인증 필요 예외가 발생한다")
    void currentUserId_rejectsInvalidPrincipal(String principalName) {
        assertEquals(AUTHENTICATION_REQUIRED,
            assertThrows(OtbooException.class, () -> service.currentUserId(principalName)).getErrorCode());
        verifyNoInteractions(users);
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
    @DisplayName("자기 자신을 팔로우하면 예외가 발생하고 저장하지 않는다")
    void create_rejectsSelfFollow() {
        assertEquals(FOLLOW_SELF_NOT_ALLOWED,
            assertThrows(OtbooException.class,
                () -> service.create(new FollowCreateRequest(me, me), me)).getErrorCode());
        verifyNoInteractions(follows, queries, users, profiles);
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
        when(users.findAllById(List.of(target, me))).thenReturn(List.of(user(me), user(target)));
        Follow anchor = follow();
        Follow lookahead = new Follow(me, UUID.randomUUID());
        var first = new FollowDto(anchor.getId(), new UserSummary(target, "name", null),
            new UserSummary(me, "name", null));
        when(queries.findList(me, true, null, null, 2, "name"))
            .thenReturn(List.of(anchor, lookahead));
        when(queries.countList(me, true, "name")).thenReturn(5L);
        var dto = service.getList(me, true, null, null, 1, "name");
        assertEquals(List.of(first), dto.data());
        assertTrue(dto.hasNext());
        assertEquals(anchor.getId(), dto.nextIdAfter());
        assertEquals(anchor.getCreatedAt().toString(), dto.nextCursor());
        assertEquals(5L, dto.totalCount());
        verify(users).findAllById(List.of(target, me));
        verify(profiles).findByUserIdIn(List.of(target, me));
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
        verify(users).findById(target);
        verifyNoMoreInteractions(users);
        verifyNoInteractions(profiles);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("검색어와 커서는 목록 조회에 전달하고 전체 개수에는 검색어만 전달한다")
    void getList_passesSearchAndCursor(boolean followings) {
        UUID userId = followings ? me : target;
        when(users.findById(userId)).thenReturn(Optional.of(user(userId)));
        Follow anchor = follow();
        Instant cursor = anchor.getCreatedAt();
        UUID idAfter = anchor.getId();
        when(follows.findById(idAfter)).thenReturn(Optional.of(anchor));
        String nameLike = "A%_!";
        when(queries.findList(userId, followings, cursor, idAfter, 11, nameLike))
            .thenReturn(List.of());
        service.getList(userId, followings, cursor.toString(), idAfter, 10, nameLike);
        verify(queries).findList(userId, followings, cursor, idAfter, 11, nameLike);
        verify(queries).countList(userId, followings, nameLike);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("팔로잉과 팔로워 조회는 idAfter의 생성 시간을 커서로 사용한다")
    void getList_resolvesIdOnlyCursor(boolean followings) {
        UUID userId = followings ? me : target;
        when(users.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(users.findAllById(List.of(target, me))).thenReturn(List.of(user(me), user(target)));
        Follow anchor = follow();
        when(follows.findById(anchor.getId())).thenReturn(Optional.of(anchor));
        when(queries.findList(userId, followings, anchor.getCreatedAt(), anchor.getId(), 11, null))
            .thenReturn(List.of(anchor));
        var dto = service.getList(userId, followings, null, anchor.getId(), 10, null);
        assertEquals(anchor.getId(), dto.data().get(0).id());
        assertEquals(target, dto.data().get(0).followee().userId());
        assertEquals(me, dto.data().get(0).follower().userId());
        assertFalse(dto.hasNext());
        verify(queries).countList(userId, followings, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("팔로잉과 팔로워 목록의 사용자 및 프로필을 각각 한 번만 일괄 조회한다")
    void getList_batchesUserSummaries(boolean followings) {
        UUID other = UUID.randomUUID();
        Follow first = followings ? new Follow(me, target) : new Follow(target, me);
        Follow second = followings ? new Follow(me, other) : new Follow(other, me);
        List<UUID> ids = followings ? List.of(target, me, other) : List.of(me, target, other);
        User targetUser = User.create("target", "target@example.com", "encoded");
        ReflectionTestUtils.setField(targetUser, "id", target);
        Profile profile = new Profile(target);
        ReflectionTestUtils.setField(profile, "profileImageUrl", "https://example.com/profile.png");
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        when(queries.findList(me, followings, null, null, 11, null)).thenReturn(List.of(first, second));
        when(users.findAllById(ids)).thenReturn(List.of(user(other), user(me), targetUser));
        when(profiles.findByUserIdIn(ids)).thenReturn(List.of(profile));

        var dto = service.getList(me, followings, null, null, 10, null);

        UserSummary targetSummary = new UserSummary(target, "target", "https://example.com/profile.png");
        UserSummary meSummary = new UserSummary(me, "name", null);
        UserSummary otherSummary = new UserSummary(other, "name", null);
        assertEquals(List.of(
            new FollowDto(first.getId(), followings ? targetSummary : meSummary,
                followings ? meSummary : targetSummary),
            new FollowDto(second.getId(), followings ? otherSummary : meSummary,
                followings ? meSummary : otherSummary)), dto.data());
        verify(users).findById(me);
        verify(users).findAllById(ids);
        verify(profiles).findByUserIdIn(ids);
        verifyNoMoreInteractions(users, profiles);
    }

    @Test
    @DisplayName("일괄 조회에서 사용자가 누락되면 기존 사용자 없음 예외를 유지한다")
    void getList_rejectsMissingUserInBatch() {
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        when(queries.findList(me, true, null, null, 11, null)).thenReturn(List.of(follow()));
        when(users.findAllById(List.of(target, me))).thenReturn(List.of(user(me)));

        assertEquals(FOLLOW_USER_NOT_FOUND,
            assertThrows(OtbooException.class,
                () -> service.getList(me, true, null, null, 10, null)).getErrorCode());
        verifyNoInteractions(profiles);
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
    @DisplayName("시간 커서만 전달하면 동일 생성 시간의 데이터 누락을 막기 위해 거부한다")
    void getList_rejectsCursorWithoutIdAfter() {
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        for (boolean followings : List.of(true, false)) {
            assertEquals(FOLLOW_INVALID_CURSOR,
                assertThrows(OtbooException.class, () -> service.getList(me, followings,
                    "2026-10-27T00:00:00Z", null, 10, null)).getErrorCode());
        }
        verifyNoInteractions(follows, queries);
    }

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    @DisplayName("시간 커서 유무와 관계없이 존재하지 않는 idAfter를 거부한다")
    void getList_rejectsMissingAnchor(boolean followings, boolean withCursor) {
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        String cursor = withCursor ? "2026-10-27T00:00:00Z" : null;

        assertEquals(FOLLOW_INVALID_CURSOR,
            assertThrows(OtbooException.class,
                () -> service.getList(me, followings, cursor, UUID.randomUUID(), 10, null)).getErrorCode());
        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    @DisplayName("시간 커서 유무와 관계없이 조회 대상의 목록에 속하지 않는 idAfter를 거부한다")
    void getList_rejectsUnrelatedAnchor(boolean followings, boolean withCursor) {
        UUID userId = followings ? target : me;
        when(users.findById(userId)).thenReturn(Optional.of(user(userId)));
        Follow anchor = follow();
        when(follows.findById(anchor.getId())).thenReturn(Optional.of(anchor));
        String cursor = withCursor ? anchor.getCreatedAt().toString() : null;

        assertEquals(FOLLOW_INVALID_CURSOR,
            assertThrows(OtbooException.class,
                () -> service.getList(userId, followings, cursor, anchor.getId(), 10, null)).getErrorCode());
        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("시간 커서가 idAfter의 실제 생성 시간과 다르면 거부한다")
    void getList_rejectsMismatchedCursor(boolean followings) {
        UUID userId = followings ? me : target;
        when(users.findById(userId)).thenReturn(Optional.of(user(userId)));
        Follow anchor = follow();
        when(follows.findById(anchor.getId())).thenReturn(Optional.of(anchor));
        String cursor = anchor.getCreatedAt().plusNanos(1).toString();

        assertEquals(FOLLOW_INVALID_CURSOR,
            assertThrows(OtbooException.class,
                () -> service.getList(userId, followings, cursor, anchor.getId(), 10, null)).getErrorCode());
        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 31, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    @DisplayName("조회 개수가 1~30 범위를 벗어나면 DB 조회 전에 거부한다")
    void getList_rejectsInvalidLimit(int limit) {
        for (boolean followings : List.of(true, false)) {
            assertEquals(FOLLOW_INVALID_CURSOR,
                assertThrows(OtbooException.class,
                    () -> service.getList(me, followings, null, null, limit, null)).getErrorCode());
        }
        verifyNoInteractions(users, follows, queries);
    }

    @ParameterizedTest
    @CsvSource({"true, 1", "true, 30", "false, 1", "false, 30"})
    @DisplayName("조회 개수의 최솟값과 최댓값을 허용하고 다음 페이지 확인을 위해 한 건 더 조회한다")
    void getList_acceptsLimitBoundaries(boolean followings, int limit) {
        when(users.findById(me)).thenReturn(Optional.of(user(me)));
        when(queries.findList(me, followings, null, null, limit + 1, null)).thenReturn(List.of());

        var dto = service.getList(me, followings, null, null, limit, null);

        assertTrue(dto.data().isEmpty());
        assertFalse(dto.hasNext());
        verify(queries).findList(me, followings, null, null, limit + 1, null);
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

