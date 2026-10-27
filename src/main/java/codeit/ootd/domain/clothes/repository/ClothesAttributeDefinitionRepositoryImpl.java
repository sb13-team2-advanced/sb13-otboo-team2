package codeit.ootd.domain.clothes.repository;

import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesAttributeDefSortBy;
import codeit.ootd.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ClothesAttributeDefinitionRepositoryImpl implements ClothesAttributeDefinitionRepositoryCustom {

    private static final String LIKE_ESCAPE = "!";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ClothesAttributeDefinition> findPage(
            String keyword,
            ClothesAttributeDefSortBy sortBy,
            SortDirection direction,
            Object cursorValue,
            UUID idAfter,
            int size) {

        boolean ascending = direction == SortDirection.ASCENDING;
        String order = ascending ? "asc" : "desc";
        String operator = ascending ? ">" : "<";
        String property = sortBy.getProperty();

        StringBuilder jpql = new StringBuilder("select d from ClothesAttributeDefinition d where 1 = 1");
        if (keyword != null) {
            jpql.append(" and lower(d.name) like :keyword escape '").append(LIKE_ESCAPE).append("'");
        }
        if (cursorValue != null) {
            jpql.append(" and (d.%s %s :cursorValue or (d.%s = :cursorValue and d.id %s :idAfter))"
                    .formatted(property, operator, property, operator));
        }
        jpql.append(" order by d.%s %s, d.id %s".formatted(property, order, order));

        TypedQuery<ClothesAttributeDefinition> query =
                entityManager.createQuery(jpql.toString(), ClothesAttributeDefinition.class);
        if (keyword != null) {
            query.setParameter("keyword", likePattern(keyword));
        }
        if (cursorValue != null) {
            query.setParameter("cursorValue", cursorValue);
            query.setParameter("idAfter", idAfter);
        }
        return query.setMaxResults(size).getResultList();
    }

    @Override
    public long countByKeyword(String keyword) {
        StringBuilder jpql = new StringBuilder("select count(d) from ClothesAttributeDefinition d");
        if (keyword != null) {
            jpql.append(" where lower(d.name) like :keyword escape '").append(LIKE_ESCAPE).append("'");
        }
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        if (keyword != null) {
            query.setParameter("keyword", likePattern(keyword));
        }
        return query.getSingleResult();
    }

    private String likePattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
