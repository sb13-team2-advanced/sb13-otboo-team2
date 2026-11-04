package codeit.ootd.domain.follow.repository;

import static codeit.ootd.domain.follow.entity.QFollow.follow;
import static codeit.ootd.domain.user.entity.QUser.user;

import codeit.ootd.domain.follow.entity.Follow;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FollowQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<Follow> findList(UUID userId, boolean followings, Instant cursor,
        UUID idAfter, int size, String nameLike) {
        return query(userId, followings, nameLike)
            .where(after(cursor, idAfter))
            .orderBy(follow.createdAt.desc(), follow.id.desc())
            .limit(size)
            .fetch();
    }

    public long countList(UUID userId, boolean followings, String nameLike) {
        Long count = query(userId, followings, nameLike).select(follow.count()).fetchOne();
        return count == null ? 0 : count;
    }

    private JPAQuery<Follow> query(UUID userId, boolean followings, String nameLike) {
        return queryFactory.selectFrom(follow)
            .join(user).on(user.id.eq(followings ? follow.followeeId : follow.followerId))
            .where((followings ? follow.followerId : follow.followeeId).eq(userId), nameContains(nameLike));
    }

    private BooleanExpression nameContains(String nameLike) {
        if (nameLike == null) return null;
        String pattern = "%" + nameLike.toLowerCase(Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return user.name.lower().like(pattern, '!');
    }

    private BooleanExpression after(Instant cursor, UUID idAfter) {
        if (cursor == null) return null;
        BooleanExpression before = follow.createdAt.lt(cursor);
        return idAfter == null ? before
            : before.or(follow.createdAt.eq(cursor).and(follow.id.lt(idAfter)));
    }
}
