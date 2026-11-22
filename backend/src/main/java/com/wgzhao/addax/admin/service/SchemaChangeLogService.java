package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.SchemaChangeLog;
import com.wgzhao.addax.admin.repository.SchemaChangeLogRepo;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class SchemaChangeLogService
{
    // Why: the page sends the column key straight through; a whitelist keeps an
    // unknown/dotted field from turning into an invalid JPA sort path.
    private static final Set<String> SORTABLE_FIELDS = Set.of(
        "changeAt", "tid", "sourceDb", "sourceTable", "columnName", "changeType");

    private final SchemaChangeLogRepo repo;

    public void recordAdd(Long tid, String sourceDb, String sourceTable, String columnName, String newSourceType, Integer newLen, Integer newPrecision, Integer newScale, String newComment)
    {
        SchemaChangeLog r = new SchemaChangeLog();
        r.setTid(tid);
        r.setSourceDb(sourceDb);
        r.setSourceTable(sourceTable);
        r.setColumnName(columnName);
        r.setChangeType("ADD");
        r.setNewSourceType(newSourceType);
        r.setNewDataLength(newLen);
        r.setNewDataPrecision(newPrecision);
        r.setNewDataScale(newScale);
        r.setNewColComment(newComment);
        r.setChangeAt(LocalDateTime.now());
        repo.save(r);
    }

    public void recordDelete(Long tid, String sourceDb, String sourceTable, String columnName, String oldSourceType, Integer oldLen, Integer oldPrecision, Integer oldScale, String oldComment)
    {
        SchemaChangeLog r = new SchemaChangeLog();
        r.setTid(tid);
        r.setSourceDb(sourceDb);
        r.setSourceTable(sourceTable);
        r.setColumnName(columnName);
        r.setChangeType("DELETE");
        r.setOldSourceType(oldSourceType);
        r.setOldDataLength(oldLen);
        r.setOldDataPrecision(oldPrecision);
        r.setOldDataScale(oldScale);
        r.setOldColComment(oldComment);
        r.setChangeAt(LocalDateTime.now());
        repo.save(r);
    }

    public void recordTypeChange(Long tid, String sourceDb, String sourceTable, String columnName, String oldType, String newType,
        Integer oldLen, Integer newLen, Integer oldPrecision, Integer newPrecision, Integer oldScale, Integer newScale,
        String oldComment, String newComment)
    {
        SchemaChangeLog r = new SchemaChangeLog();
        r.setTid(tid);
        r.setSourceDb(sourceDb);
        r.setSourceTable(sourceTable);
        r.setColumnName(columnName);
        r.setChangeType("TYPE_CHANGE");
        r.setOldSourceType(oldType);
        r.setNewSourceType(newType);
        r.setOldDataLength(oldLen);
        r.setNewDataLength(newLen);
        r.setOldDataPrecision(oldPrecision);
        r.setNewDataPrecision(newPrecision);
        r.setOldDataScale(oldScale);
        r.setNewDataScale(newScale);
        r.setOldColComment(oldComment);
        r.setNewColComment(newComment);
        r.setChangeAt(LocalDateTime.now());
        repo.save(r);
    }

    public Page<SchemaChangeLog> search(int page, int size, String changeType, String keyword,
        LocalDate from, LocalDate to, String sortField, String sortOrder)
    {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 500);
        Sort sort = Sort.by(Sort.Direction.DESC, "changeAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));
        if (sortField != null && SORTABLE_FIELDS.contains(sortField)) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
            sort = Sort.by(direction, sortField).and(Sort.by(Sort.Direction.DESC, "id"));
        }
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Specification<SchemaChangeLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (changeType != null && !changeType.isBlank()) {
                predicates.add(cb.equal(root.get("changeType"), changeType.trim()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("sourceDb")), like),
                    cb.like(cb.lower(root.get("sourceTable")), like),
                    cb.like(cb.lower(root.get("columnName")), like)));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("changeAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("changeAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repo.findAll(spec, pageable);
    }
}
