package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.FailureCategory;
import com.wgzhao.addax.admin.dto.FailureCleanupRequest;
import com.wgzhao.addax.admin.dto.FailureCleanupResult;
import com.wgzhao.addax.admin.dto.FailureDetailDto;
import com.wgzhao.addax.admin.dto.FailureDto;
import com.wgzhao.addax.admin.dto.FailureGroupDto;
import com.wgzhao.addax.admin.dto.FailureStatsDto;
import com.wgzhao.addax.admin.dto.LatestFailureDto;
import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.model.EtlFailure;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.AddaxLogRepo;
import com.wgzhao.addax.admin.repository.EtlExecutionRepo;
import com.wgzhao.addax.admin.repository.EtlFailureRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Read, aggregate and lifecycle operations over failure facts.
 */
@Service
@Slf4j
public class FailureService
{
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DELETE_BATCH = 10_000;

    private final EtlFailureRepo failureRepo;
    private final VwEtlTableWithSourceRepo tableViewRepo;
    private final EtlExecutionRepo executionRepo;
    private final AddaxLogRepo addaxLogRepo;
    private final AuthzService authzService;
    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;

    public FailureService(EtlFailureRepo failureRepo, VwEtlTableWithSourceRepo tableViewRepo,
        EtlExecutionRepo executionRepo, AddaxLogRepo addaxLogRepo, AuthzService authzService,
        JdbcTemplate jdbcTemplate,
        @Value("${COLLECTION_FAILURE_RETENTION_DAYS:365}") int retentionDays)
    {
        this.failureRepo = failureRepo;
        this.tableViewRepo = tableViewRepo;
        this.executionRepo = executionRepo;
        this.addaxLogRepo = addaxLogRepo;
        this.authzService = authzService;
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = retentionDays;
    }

    public PageResponse<FailureDto> list(int page, int pageSize, Long tid, Integer sourceId,
        String stage, String category, String fingerprint, LocalDate from, LocalDate to, String q,
        boolean finalOnly)
    {
        Specification<EtlFailure> spec = buildSpec(tid, sourceId, stage, category, fingerprint, from, to, q, finalOnly);
        Page<EtlFailure> result = failureRepo.findAll(spec,
            PageRequest.of(Math.max(page, 0), Math.max(pageSize, 1),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        Page<FailureDto> mapped = result.map(failure -> toDto(failure, false, false));
        return PageResponse.from(mapped);
    }

    /**
     * Latest failure per table, used by the list page's status icon. One query for a page of rows,
     * not one query per row.
     */
    public Map<Long, LatestFailureDto> latestForTables(Collection<Long> tids)
    {
        Map<Long, LatestFailureDto> result = new LinkedHashMap<>();
        if (tids == null || tids.isEmpty()) {
            return result;
        }
        List<Long> ids = tids.stream().distinct().limit(200).toList();
        for (EtlFailure failure : failureRepo.findLatestForTids(ids)) {
            result.put(failure.getTid(), latestOf(failure));
        }
        for (Long tid : ids) {
            result.computeIfAbsent(tid, this::emptyLatest);
        }
        return result;
    }

    public LatestFailureDto latest(long tid)
    {
        Optional<EtlFailure> latest = failureRepo.findFirstByTidOrderByOccurredAtDescIdDesc(tid);
        return latest.map(this::latestOf).orElseGet(() -> emptyLatest(tid));
    }

    public FailureDetailDto detail(long id)
    {
        EtlFailure failure = failureRepo.findById(id).orElse(null);
        if (failure == null) {
            return null;
        }
        boolean canView = canViewOperational(failure.getSourceId());
        return new FailureDetailDto(
            toDto(failure, true, canView),
            occurrences(failure),
            evidence(failure, canView),
            hintFor(failure.getCategory()));
    }

    /**
     * Summary used by the legacy {@code /tasks/{tid}/last-error} endpoint during the transition.
     */
    public String latestSummary(long tid)
    {
        return failureRepo.findFirstByTidOrderByOccurredAtDescIdDesc(tid)
            .map(EtlFailure::getSummary)
            .orElse(null);
    }

    public FailureCleanupResult cleanup(FailureCleanupRequest request)
    {
        LocalDate before = request.before();
        List<Object> args = new ArrayList<>();
        String where = cleanupWhere(before, request.tid(), request.sourceId(), request.stage(), args);
        long matched = jdbcTemplate.queryForObject(
            "select count(*) from public.etl_failure " + where, Long.class, args.toArray());
        if (request.dryRun()) {
            return new FailureCleanupResult(matched, 0, true);
        }
        long deleted = 0;
        while (true) {
            int batch = jdbcTemplate.update("""
                delete from public.etl_failure where id in (
                    select id from public.etl_failure %s order by id limit %d
                )
                """.formatted(where, DELETE_BATCH), args.toArray());
            deleted += batch;
            if (batch < DELETE_BATCH) {
                break;
            }
        }
        return new FailureCleanupResult(matched, deleted, false);
    }

    public FailureStatsDto stats(LocalDate from, LocalDate to, Long tid, Integer sourceId,
        String stage, String category)
    {
        List<Object> args = new ArrayList<>();
        String where = listWhere(from, to, tid, sourceId, stage, category, null, false, args);
        long[] totals = jdbcTemplate.queryForObject(
            "select count(*), count(distinct tid) from public.etl_failure " + where,
            (rs, rowNum) -> new long[] {rs.getLong(1), rs.getLong(2)}, args.toArray());
        Map<String, Long> byStage = counts("select stage, count(*) from public.etl_failure "
            + where + " group by stage order by count(*) desc", args);
        Map<String, Long> byCategory = counts("select category, count(*) from public.etl_failure "
            + where + " group by category order by count(*) desc", args);
        return new FailureStatsDto(totals == null ? 0 : totals[0], totals == null ? 0 : totals[1],
            byStage, byCategory);
    }

    public List<FailureGroupDto> groups(LocalDate from, LocalDate to, Long tid, Integer sourceId,
        String stage, String category, String q, boolean finalOnly, int limit)
    {
        List<Object> args = new ArrayList<>();
        String where = listWhere(from, to, tid, sourceId, stage, category, q, finalOnly, args);
        int capped = Math.min(Math.max(limit, 1), 500);
        String sql = """
            select fingerprint, tid, stage, category,
                   (array_agg(summary order by occurred_at desc))[1] as summary,
                   count(*) as cnt,
                   min(occurred_at) as first_seen,
                   max(occurred_at) as last_seen,
                   max(source_db) as source_db,
                   max(source_table) as source_table,
                   max(target_db) as target_db,
                   max(target_table) as target_table
            from public.etl_failure %s
            group by fingerprint, tid, stage, category
            order by cnt desc, last_seen desc
            limit %d
            """.formatted(where, capped);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new FailureGroupDto(
            rs.getString("fingerprint"),
            rs.getLong("tid"),
            rs.getString("stage"),
            rs.getString("category"),
            rs.getString("summary"),
            rs.getLong("cnt"),
            format(rs.getTimestamp("first_seen")),
            format(rs.getTimestamp("last_seen")),
            rs.getString("source_db"),
            rs.getString("source_table"),
            rs.getString("target_db"),
            rs.getString("target_table")
        ), args.toArray());
    }

    /**
     * Failures outlive raw logs but not forever. The minimum guard prevents a typo from erasing
     * recent history that an operator still needs.
     */
    @Scheduled(cron = "0 40 3 * * *")
    public void purgeExpired()
    {
        int days = Math.max(retentionDays, 30);
        LocalDate cutoff = LocalDate.now().minusDays(days);
        FailureCleanupResult result = cleanup(new FailureCleanupRequest(cutoff, null, null, null, false));
        if (result.deleted() > 0) {
            log.info("Purged {} failure fact(s) older than {} ({} days)",
                result.deleted(), cutoff, days);
        }
    }

    private LatestFailureDto latestOf(EtlFailure failure)
    {
        boolean canView = canViewOperational(failure.getSourceId());
        return new LatestFailureDto(true, null,
            toDto(failure, true, canView),
            occurrences(failure),
            evidence(failure, canView),
            hintFor(failure.getCategory()));
    }

    private LatestFailureDto.Occurrences occurrences(EtlFailure failure)
    {
        List<Object[]> summary = failureRepo.summarizeOccurrences(failure.getTid(), failure.getFingerprint());
        if (summary.isEmpty() || summary.getFirst().length < 3) {
            return new LatestFailureDto.Occurrences(1, null, null);
        }
        Object[] row = summary.getFirst();
        long count = row[0] == null ? 1 : ((Number) row[0]).longValue();
        return new LatestFailureDto.Occurrences(count, asString(row[1]), asString(row[2]));
    }

    private LatestFailureDto emptyLatest(long tid)
    {
        VwEtlTableWithSource table = tableViewRepo.findById(tid).orElse(null);
        String status = table == null ? null : table.getStatus();
        boolean failedState = "E".equals(status) || "U".equals(status);
        if (!failedState) {
            return new LatestFailureDto(false, LatestFailureDto.NO_FAILURE, null,
                LatestFailureDto.Occurrences.empty(),
                new LatestFailureDto.Evidence("missing", "missing", "missing"),
                "该采集表暂无失败记录。");
        }
        // A failed execution without a failure fact means the facts were cleaned, not that the
        // history never existed. Execution facts have a longer lifecycle than failure facts.
        boolean purged = executionRepo.existsByTidAndStatus(tid, EtlExecutionService.FAILED);
        if (purged) {
            return new LatestFailureDto(false, LatestFailureDto.PURGED, null,
                LatestFailureDto.Occurrences.empty(),
                new LatestFailureDto.Evidence("missing", "missing", "available"),
                "没有找到对应的失败记录（可能已按保留策略清理），执行快照仍可查看。");
        }
        return new LatestFailureDto(false, LatestFailureDto.NO_EVIDENCE_FOR_STATE, null,
            LatestFailureDto.Occurrences.empty(),
            new LatestFailureDto.Evidence("missing", "missing", "missing"),
            "状态标记为失败，但没有对应的失败记录。可能是记录已清理、部署前遗留状态，或状态被手工修改。");
    }

    private LatestFailureDto.Evidence evidence(EtlFailure failure, boolean canView)
    {
        String detail = failure.getDetail() == null || failure.getDetail().isBlank()
            ? "missing"
            : (canView ? "available" : "restricted");
        String log;
        if (failure.getLogId() == null) {
            log = "missing";
        }
        else if (!canView) {
            log = "restricted";
        }
        else {
            log = addaxLogRepo.existsById(failure.getLogId()) ? "available" : "purged";
        }
        String execution = failure.getExecutionId() != null
            && executionRepo.existsById(failure.getExecutionId()) ? "available" : "missing";
        return new LatestFailureDto.Evidence(detail, log, execution);
    }

    private boolean canViewOperational(Integer sourceId)
    {
        return authzService.isAdmin()
            || (sourceId != null && authzService.canOperateSource(sourceId));
    }

    private FailureDto toDto(EtlFailure failure, boolean includeDetail, boolean canView)
    {
        return new FailureDto(
            failure.getId(),
            failure.getTid(),
            failure.getExecutionId(),
            failure.getJobQueueId(),
            failure.getLogId(),
            failure.getLogName(),
            format(failure.getOccurredAt()),
            failure.getStage(),
            failure.getCategory(),
            failure.getErrorClass(),
            failure.getErrorCode(),
            failure.getSummary(),
            includeDetail && canView ? failure.getDetail() : null,
            failure.getAttemptNo(),
            failure.getMaxAttempts(),
            failure.isFinalAttempt(),
            failure.getRunType(),
            failure.getBizDate() == null ? null : failure.getBizDate().toString(),
            failure.getSourceId(),
            failure.getSourceDb(),
            failure.getSourceTable(),
            failure.getTargetDb(),
            failure.getTargetTable()
        );
    }

    private Specification<EtlFailure> buildSpec(Long tid, Integer sourceId, String stage,
        String category, String fingerprint, LocalDate from, LocalDate to, String q, boolean finalOnly)
    {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (tid != null) {
                predicates.add(cb.equal(root.get("tid"), tid));
            }
            if (sourceId != null) {
                predicates.add(cb.equal(root.get("sourceId"), sourceId));
            }
            if (stage != null && !stage.isBlank()) {
                predicates.add(cb.equal(root.get("stage"), stage));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (fingerprint != null && !fingerprint.isBlank()) {
                predicates.add(cb.equal(root.get("fingerprint"), fingerprint));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay()));
            }
            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("summary")),
                    "%" + q.toLowerCase(Locale.ROOT) + "%"));
            }
            if (finalOnly) {
                predicates.add(cb.isTrue(root.get("finalAttempt")));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Map<String, Long> counts(String sql, List<Object> args)
    {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        }, args.toArray());
        return result;
    }

    private String cleanupWhere(LocalDate before, Long tid, Integer sourceId, String stage, List<Object> args)
    {
        StringBuilder where = new StringBuilder("where occurred_at < ?");
        args.add(before.atStartOfDay());
        if (tid != null) {
            where.append(" and tid = ?");
            args.add(tid);
        }
        if (sourceId != null) {
            where.append(" and source_id = ?");
            args.add(sourceId);
        }
        if (stage != null && !stage.isBlank()) {
            where.append(" and stage = ?");
            args.add(stage);
        }
        return where.toString();
    }

    private String listWhere(LocalDate from, LocalDate to, Long tid, Integer sourceId,
        String stage, String category, String q, boolean finalOnly, List<Object> args)
    {
        StringBuilder where = new StringBuilder("where 1=1");
        if (from != null) {
            where.append(" and occurred_at >= ?");
            args.add(from.atStartOfDay());
        }
        if (to != null) {
            where.append(" and occurred_at < ?");
            args.add(to.plusDays(1).atStartOfDay());
        }
        if (tid != null) {
            where.append(" and tid = ?");
            args.add(tid);
        }
        if (sourceId != null) {
            where.append(" and source_id = ?");
            args.add(sourceId);
        }
        if (stage != null && !stage.isBlank()) {
            where.append(" and stage = ?");
            args.add(stage);
        }
        if (category != null && !category.isBlank()) {
            where.append(" and category = ?");
            args.add(category);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and summary ilike ?");
            args.add("%" + q + "%");
        }
        if (finalOnly) {
            where.append(" and final_attempt = true");
        }
        return where.toString();
    }

    private static String hintFor(String category)
    {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case FailureCategory.TABLE_NOT_FOUND -> "源表可能已被重命名或删除，建议刷新表结构后重试。";
            case FailureCategory.OOM -> "进程可能因内存不足被系统终止，请检查内存与并发配置。";
            case FailureCategory.NETWORK -> "检查源端或目标端网络与连接配置。";
            case FailureCategory.AUTH -> "检查账号权限与密码。";
            case FailureCategory.TIMEOUT -> "检查超时配置与源端负载。";
            case FailureCategory.DISK -> "检查磁盘空间或配额。";
            case FailureCategory.CONTRACT -> "目标表结构与契约不一致，请检查目标表或外部表结构。";
            case FailureCategory.DATA -> "检查脏数据或字段类型转换。";
            default -> null;
        };
    }

    private static String format(LocalDateTime value)
    {
        return value == null ? null : value.format(TIME_FMT);
    }

    private static String format(Timestamp value)
    {
        return value == null ? null : value.toLocalDateTime().format(TIME_FMT);
    }

    private static String asString(Object value)
    {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return format(timestamp);
        }
        if (value instanceof LocalDateTime dateTime) {
            return format(dateTime);
        }
        return value.toString();
    }
}
