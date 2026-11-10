package codeit.ootd.domain.feed.repository;

import static codeit.ootd.domain.feed.entity.QFeed.feed;

import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.global.enums.SortDirection;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeedQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Feed> findList(FeedSearchRequest request, int size) {
        boolean ascending =
                request.sortDirection() == SortDirection.ASCENDING;

        return queryFactory
                .selectFrom(feed)
                .where(
                        contentContains(request.keywordLike()),
                        authorEquals(request.authorIdEqual()),
                        skyStatusEquals(request.skyStatusEqual()),
                        precipitationTypeEquals(
                                request.precipitationTypeEqual()
                        ),
                        afterCursor(request, ascending)
                )
                .orderBy(
                        sortOrder(request.sortBy(), ascending),
                        ascending ? feed.id.asc() : feed.id.desc()
                )
                .limit(size)
                .fetch();
    }

    public long countList(FeedSearchRequest request) {
        Long count = queryFactory
                .select(feed.count())
                .from(feed)
                .where(
                        contentContains(request.keywordLike()),
                        authorEquals(request.authorIdEqual()),
                        skyStatusEquals(request.skyStatusEqual()),
                        precipitationTypeEquals(
                                request.precipitationTypeEqual()
                        )
                )
                .fetchOne();

        return count == null ? 0L : count;
    }

    private BooleanExpression afterCursor(
            FeedSearchRequest request,
            boolean ascending
    ) {
        if (request.cursor() == null) {
            return null;
        }

        BooleanExpression after;
        BooleanExpression sameValue;

        switch (request.sortBy()) {
            case "createdAt" -> {
                Instant cursor = Instant.parse(request.cursor());

                after = ascending
                        ? feed.createdAt.gt(cursor)
                        : feed.createdAt.lt(cursor);

                sameValue = feed.createdAt.eq(cursor);
            }
            case "likeCount" -> {
                long cursor = Long.parseLong(request.cursor());

                after = ascending
                        ? feed.likeCount.gt(cursor)
                        : feed.likeCount.lt(cursor);

                sameValue = feed.likeCount.eq(cursor);
            }
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 피드 정렬 기준입니다: " + request.sortBy()
            );
        }

        if (request.idAfter() == null) {
            return after;
        }

        BooleanExpression afterId = ascending
                ? feed.id.gt(request.idAfter())
                : feed.id.lt(request.idAfter());

        return after.or(sameValue.and(afterId));
    }

    private BooleanExpression contentContains(String keyword) {
        return keyword == null
                ? null
                : feed.content.containsIgnoreCase(keyword);
    }

    private BooleanExpression authorEquals(UUID authorId) {
        return authorId == null
                ? null
                : feed.authorId.eq(authorId);
    }

    private BooleanExpression skyStatusEquals(String skyStatus) {
        return skyStatus == null
                ? null
                : feed.weatherSkyStatus.eq(skyStatus);
    }

    private BooleanExpression precipitationTypeEquals(
            String precipitationType
    ) {
        return precipitationType == null
                ? null
                : feed.weatherPrecipitationType.eq(precipitationType);
    }

    private OrderSpecifier<?> sortOrder(
            String sortBy,
            boolean ascending
    ) {
        return switch (sortBy) {
            case "createdAt" ->
                    ascending ? feed.createdAt.asc() : feed.createdAt.desc();
            case "likeCount" ->
                    ascending ? feed.likeCount.asc() : feed.likeCount.desc();
            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 피드 정렬 기준입니다: " + sortBy
                    );
        };
    }
}