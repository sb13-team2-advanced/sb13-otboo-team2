package codeit.ootd.domain.clothes.repository;

import static codeit.ootd.domain.clothes.entity.QClothes.clothes;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.enums.ClothesType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 의상 목록 커서 조회. 최신순(createdAt DESC)이고 같은 시각이면 id DESC로 순서를 고정한다.
 */
@Repository
@RequiredArgsConstructor
public class ClothesQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Clothes> findList(UUID ownerId, ClothesType type, Instant cursor, UUID idAfter, int size) {
        return query(ownerId, type)
                .where(after(cursor, idAfter))
                .orderBy(clothes.createdAt.desc(), clothes.id.desc())
                .limit(size)
                .fetch();
    }

    public long countList(UUID ownerId, ClothesType type) {
        Long count = query(ownerId, type).select(clothes.count()).fetchOne();
        return count == null ? 0 : count;
    }

    private JPAQuery<Clothes> query(UUID ownerId, ClothesType type) {
        return queryFactory.selectFrom(clothes)
                .where(clothes.userId.eq(ownerId), typeEquals(type));
    }

    private BooleanExpression typeEquals(ClothesType type) {
        return type == null ? null : clothes.type.eq(type);
    }

    private BooleanExpression after(Instant cursor, UUID idAfter) {
        if (cursor == null) {
            return null;
        }
        BooleanExpression before = clothes.createdAt.lt(cursor);
        return idAfter == null
                ? before
                : before.or(clothes.createdAt.eq(cursor).and(clothes.id.lt(idAfter)));
    }
}
