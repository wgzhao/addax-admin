package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.dto.TableListCriteria;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Predicates of the collection-table list.
 *
 * <p>Every user-provided LIKE text is escaped, otherwise a value containing {@code %} or {@code _}
 * silently widens the match into a full scan of unexpected rows.</p>
 */
final class TableSpecifications
{
    private static final char LIKE_ESCAPE = '\\';

    private TableSpecifications()
    {
    }

    static Specification<VwEtlTableWithSource> byCriteria(TableListCriteria criteria, Collection<Integer> allowedSourceIds)
    {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addAllowedSources(predicates, root, cb, allowedSourceIds);
            addScope(predicates, root, cb, criteria.scope());
            addIn(predicates, root, cb, "status", criteria.statuses());
            addIn(predicates, root, cb, "sid", criteria.sourceIds());
            // db_type is stored inconsistently by older rows (MYSQL vs mysql), so compare folded.
            addInIgnoreCase(predicates, root, cb, "dbType", criteria.dbTypes());
            addEqual(predicates, cb, root.get("enabled"), criteria.sourceEnabled());
            addIn(predicates, root, cb, "targetId", criteria.targetIds());
            addIn(predicates, root, cb, "targetType", criteria.targetTypes());
            addEqual(predicates, cb, root.get("targetEnabled"), criteria.targetEnabled());
            addIn(predicates, root, cb, "writeMode", criteria.writeModes());
            addIn(predicates, root, cb, "schemaManagementMode", toSchemaModes(criteria.schemaModes()));
            addIntRange(predicates, cb, root.get("duration"), criteria.durationFrom(), criteria.durationTo());
            addContains(predicates, cb, root.get("targetDb"), criteria.targetDbContains());
            addContains(predicates, cb, root.get("tblComment"), criteria.tblCommentContains());
            addContains(predicates, cb, root.get("filterColumn"), criteria.q());
            addDateRange(predicates, root, cb, "endTime", criteria.endTimeFrom(), criteria.endTimeTo());
            addDateRange(predicates, root, cb, "createdAt", criteria.createdFrom(), criteria.createdTo());
            addDateRange(predicates, root, cb, "updatedAt", criteria.updatedFrom(), criteria.updatedTo());
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addAllowedSources(List<Predicate> predicates, Root<VwEtlTableWithSource> root,
        CriteriaBuilder cb, Collection<Integer> allowedSourceIds)
    {
        if (allowedSourceIds != null) {
            predicates.add(root.get("sid").in(allowedSourceIds));
        }
    }

    private static void addScope(List<Predicate> predicates, Root<VwEtlTableWithSource> root,
        CriteriaBuilder cb, String scope)
    {
        if ("CURRENT".equals(scope)) {
            predicates.add(cb.isNull(root.get("retiredAt")));
        }
        else if ("DORMANT".equals(scope)) {
            predicates.add(cb.isNull(root.get("retiredAt")));
            predicates.add(cb.or(
                cb.isFalse(root.get("enabled")),
                cb.equal(root.get("status"), TableStatus.EXCLUDE_COLLECT)));
        }
        else if ("RETIRED".equals(scope)) {
            predicates.add(cb.isNotNull(root.get("retiredAt")));
        }
    }

    private static <T> void addIn(List<Predicate> predicates, Root<VwEtlTableWithSource> root,
        CriteriaBuilder cb, String field, Collection<T> values)
    {
        if (values != null && !values.isEmpty()) {
            predicates.add(root.get(field).in(values));
        }
    }

    private static void addInIgnoreCase(List<Predicate> predicates, Root<VwEtlTableWithSource> root,
        CriteriaBuilder cb, String field, Collection<String> values)
    {
        if (values != null && !values.isEmpty()) {
            List<String> folded = values.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
            predicates.add(cb.lower(root.get(field)).in(folded));
        }
    }

    private static void addEqual(List<Predicate> predicates, CriteriaBuilder cb,
        Expression<?> path, Boolean value)
    {
        if (value != null) {
            predicates.add(cb.equal(path, value));
        }
    }

    private static void addContains(List<Predicate> predicates, CriteriaBuilder cb,
        Expression<String> path, String raw)
    {
        if (raw != null && !raw.isBlank()) {
            predicates.add(cb.like(cb.upper(path), likePattern(raw), LIKE_ESCAPE));
        }
    }

    private static void addIntRange(List<Predicate> predicates, CriteriaBuilder cb,
        Expression<Integer> path, Integer from, Integer to)
    {
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(path, from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(path, to));
        }
    }

    private static void addDateRange(List<Predicate> predicates, Root<VwEtlTableWithSource> root,
        CriteriaBuilder cb, String field, LocalDate from, LocalDate to)
    {
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(field), from.atStartOfDay()));
        }
        if (to != null) {
            // Inclusive end date: everything before the next day's midnight.
            predicates.add(cb.lessThan(root.get(field), to.plusDays(1).atStartOfDay()));
        }
    }

    private static List<SchemaManagementMode> toSchemaModes(List<String> values)
    {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<SchemaManagementMode> modes = new ArrayList<>(values.size());
        for (String value : values) {
            modes.add(SchemaManagementMode.valueOf(value.toUpperCase(Locale.ROOT)));
        }
        return modes;
    }

    private static String likePattern(String raw)
    {
        String escaped = raw.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped.toUpperCase(Locale.ROOT) + "%";
    }
}
