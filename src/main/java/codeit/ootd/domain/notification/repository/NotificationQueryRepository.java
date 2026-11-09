package codeit.ootd.domain.notification.repository;

import static codeit.ootd.domain.notification.entity.QNotification.notification;

import codeit.ootd.domain.notification.entity.Notification;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Notification> findList(UUID receiverId, Instant cursor, UUID idAfter, int size) {
        return queryFactory.selectFrom(notification)
                .where(notification.receiverId.eq(receiverId), after(cursor, idAfter))
                .orderBy(notification.createdAt.desc(), notification.id.desc())
                .limit(size)
                .fetch();
    }

    public long countByReceiverId(UUID receiverId) {
        Long count = queryFactory.select(notification.count()).from(notification)
                .where(notification.receiverId.eq(receiverId))
                .fetchOne();
        return count == null ? 0 : count;
    }

    private BooleanExpression after(Instant cursor, UUID idAfter) {
        if (cursor == null) return null;
        return notification.createdAt.lt(cursor)
                .or(notification.createdAt.eq(cursor).and(notification.id.lt(idAfter)));
    }
}
