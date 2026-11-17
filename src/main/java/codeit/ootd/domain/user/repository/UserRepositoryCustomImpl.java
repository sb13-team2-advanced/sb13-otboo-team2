package codeit.ootd.domain.user.repository;

import codeit.ootd.domain.user.entity.QUser;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public UserRepositoryCustomImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Slice<User> findUsers(
            String cursor,
            UUID idAfter,
            int limit,
            String sortBy,
            String sortDirection,
            String emailLike,
            UserRole roleEqual,
            Boolean locked
    ) {
        QUser user = QUser.user;


        boolean descending = "DESCENDING".equals(sortDirection);

        OrderSpecifier<?> primaryOrder = switch (sortBy) {
            case "email" -> descending
                    ? user.email.desc()
                    : user.email.asc();

            case "createdAt" -> descending
                    ? user.createdAt.desc()
                    : user.createdAt.asc();

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 정렬 기준입니다: " + sortBy
            );
        };

        OrderSpecifier<UUID> idOrder = descending
                ? user.id.desc()
                : user.id.asc();

        BooleanExpression cursorCondition = null;

        if (cursor != null && !cursor.isBlank() && idAfter != null) {

            if ("email".equals(sortBy)) {
                cursorCondition = descending
                        ? user.email.lt(cursor)
                          .or(user.email.eq(cursor)
                              .and(user.id.lt(idAfter)))
                        : user.email.gt(cursor)
                          .or(user.email.eq(cursor)
                              .and(user.id.gt(idAfter)));

            } else if ("createdAt".equals(sortBy)) {
                Instant cursorTime = Instant.parse(cursor);

                cursorCondition = descending
                        ? user.createdAt.lt(cursorTime)
                          .or(user.createdAt.eq(cursorTime)
                              .and(user.id.lt(idAfter)))
                        : user.createdAt.gt(cursorTime)
                          .or(user.createdAt.eq(cursorTime)
                              .and(user.id.gt(idAfter)));
            }
        }

        List<User> users = queryFactory
                .selectFrom(user)
                .where(
                        cursorCondition,
                        emailCondition(user, emailLike),
                        roleCondition(user, roleEqual),
                        lockedCondition(user, locked)
                )
                .orderBy(primaryOrder, idOrder)
                .limit((long) limit + 1)
                .fetch();

        boolean hasNext = users.size() > limit;

        List<User> content = hasNext
                ? users.subList(0, limit)
                : users;

        return new SliceImpl<>(
                content,
                PageRequest.of(0, limit),
                hasNext
        );
    }

    @Override
    public long countUsers(
            String emailLike,
            UserRole roleEqual,
            Boolean locked
    ) {
        QUser user = QUser.user;

        Long count = queryFactory
                .select(user.count())
                .from(user)
                .where(
                        emailCondition(user, emailLike),
                        roleCondition(user, roleEqual),
                        lockedCondition(user, locked)
                )
                .fetchOne();

        return count != null ? count : 0L;
    }

    private BooleanExpression emailCondition(
            QUser user,
            String emailLike
    ) {
        return emailLike == null || emailLike.isBlank()
                ? null
                : user.email.containsIgnoreCase(emailLike);
    }

    private BooleanExpression roleCondition(
            QUser user,
            UserRole roleEqual
    ) {
        return roleEqual == null
                ? null
                : user.role.eq(roleEqual);
    }

    private BooleanExpression lockedCondition(
            QUser user,
            Boolean locked
    ) {
        return locked == null
                ? null
                : user.locked.eq(locked);
    }



}
