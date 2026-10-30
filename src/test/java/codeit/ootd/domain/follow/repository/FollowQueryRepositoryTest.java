package codeit.ootd.domain.follow.repository;

import static codeit.ootd.domain.follow.entity.QFollow.follow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.follow.entity.Follow;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowQueryRepositoryTest {
    @Mock JPAQueryFactory queryFactory;
    @InjectMocks FollowQueryRepository repository;

    @Test
    @DisplayName("팔로잉 목록에 검색어 이스케이프, 복합 커서, 정렬과 조회 개수를 적용한다")
    void findList_followingsAppliesSearchAndCursor() {
        TestQuery<Follow> query = new TestQuery<>();
        when(queryFactory.selectFrom(follow)).thenReturn(query);
        Follow result = new Follow(UUID.randomUUID(), UUID.randomUUID());
        query.results = List.of(result);
        Instant cursor = Instant.parse("2026-10-27T00:00:00Z");
        UUID idAfter = UUID.randomUUID();

        assertEquals(List.of(result), repository.findList(result.getFollowerId(), true, cursor, idAfter, 11, "A%_!"));

        String conditions = query.getMetadata().getWhere().toString();
        assertTrue(conditions.contains("follow.followerId = " + result.getFollowerId()));
        assertTrue(conditions.contains("%a!%!_!!%"));
        assertTrue(conditions.contains("follow.createdAt < " + cursor));
        assertTrue(conditions.contains("follow.createdAt = " + cursor));
        assertTrue(conditions.contains("follow.id < " + idAfter));
        assertEquals(11L, query.getMetadata().getModifiers().getLimit());
        assertEquals(List.of(follow.createdAt.desc(), follow.id.desc()), query.getMetadata().getOrderBy());
        assertTrue(query.toString().contains("user.id = follow.followeeId"));
    }

    @Test
    @DisplayName("검색어와 커서가 없는 팔로워 조회는 해당 선택 조건을 생략한다")
    void findList_followersOmitsOptionalConditions() {
        TestQuery<Follow> query = new TestQuery<>();
        when(queryFactory.selectFrom(follow)).thenReturn(query);
        UUID userId = UUID.randomUUID();

        assertTrue(repository.findList(userId, false, null, null, 10, null).isEmpty());

        assertEquals("follow.followeeId = " + userId, query.getMetadata().getWhere().toString());
        assertTrue(query.toString().contains("user.id = follow.followerId"));
    }

    @Test
    @DisplayName("보조 커서가 없으면 생성 시간만으로 다음 목록을 조회한다")
    void findList_withoutIdAfterUsesTimeOnly() {
        TestQuery<Follow> query = new TestQuery<>();
        when(queryFactory.selectFrom(follow)).thenReturn(query);
        Instant cursor = Instant.parse("2026-10-27T00:00:00Z");

        repository.findList(UUID.randomUUID(), true, cursor, null, 10, null);

        String conditions = query.getMetadata().getWhere().toString();
        assertTrue(conditions.contains("follow.createdAt < " + cursor));
        assertFalse(conditions.contains("follow.id <"));
    }

    @Test
    @DisplayName("전체 개수는 검색 조건만 적용하며 커서와 조회 개수 제한을 사용하지 않는다")
    void countList_countsFilteredRelations() {
        TestQuery<Follow> query = new TestQuery<>();
        when(queryFactory.selectFrom(follow)).thenReturn(query);
        query.single = 7L;

        assertEquals(7L, repository.countList(UUID.randomUUID(), false, "Name"));

        assertTrue(query.getMetadata().getWhere().toString().contains("%name%"));
        assertFalse(query.getMetadata().getWhere().toString().contains("createdAt"));
        assertNull(query.getMetadata().getModifiers().getLimit());
        assertTrue(query.toString().startsWith("select count(follow)"));
    }

    @Test
    @DisplayName("전체 개수 쿼리 결과가 null이면 0을 반환한다")
    void countList_returnsZeroForNull() {
        TestQuery<Follow> query = new TestQuery<>();
        when(queryFactory.selectFrom(follow)).thenReturn(query);

        assertEquals(0L, repository.countList(UUID.randomUUID(), true, null));
    }
    private static class TestQuery<T> extends JPAQuery<T> {
        private List<T> results = List.of();
        private Object single;

        @Override
        public List<T> fetch() {
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T fetchOne() {
            return (T) single;
        }
    }
}


