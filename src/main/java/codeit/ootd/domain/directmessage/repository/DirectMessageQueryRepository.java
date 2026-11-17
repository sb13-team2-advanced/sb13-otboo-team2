package codeit.ootd.domain.directmessage.repository;

import static codeit.ootd.domain.directmessage.entity.QDirectMessage.directMessage;

import codeit.ootd.domain.directmessage.entity.DirectMessage;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DirectMessageQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<DirectMessage> findList(UUID currentUserId, UUID userId,
            Instant cursor, UUID idAfter, int size) {
        return queryFactory.selectFrom(directMessage)
                .where(conversation(currentUserId, userId), after(cursor, idAfter))
                .orderBy(directMessage.createdAt.desc(), directMessage.id.desc())
                .limit(size)
                .fetch();
    }

    public long countConversation(UUID currentUserId, UUID userId) {
        Long count = queryFactory.select(directMessage.count()).from(directMessage)
                .where(conversation(currentUserId, userId))
                .fetchOne();
        return count == null ? 0 : count;
    }

    private BooleanExpression conversation(UUID currentUserId, UUID userId) {
        return directMessage.senderId.eq(currentUserId).and(directMessage.receiverId.eq(userId))
                .or(directMessage.senderId.eq(userId).and(directMessage.receiverId.eq(currentUserId)));
    }

    private BooleanExpression after(Instant cursor, UUID idAfter) {
        if (cursor == null) return null;
        return directMessage.createdAt.lt(cursor)
                .or(directMessage.createdAt.eq(cursor).and(directMessage.id.lt(idAfter)));
    }
}
