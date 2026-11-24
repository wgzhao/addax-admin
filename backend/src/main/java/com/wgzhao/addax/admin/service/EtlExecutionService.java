package com.wgzhao.addax.admin.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.dto.TargetContractReport;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlExecution;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlExecutionRepo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the immutable execution context. Historical facts point here instead of
 * resolving display data from mutable source, target, or table configuration.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EtlExecutionService
{
    public static final int SNAPSHOT_VERSION = 3;
    public static final String RUNNING = "running";
    public static final String SUCCEEDED = "succeeded";
    public static final String FAILED = "failed";

    private final EtlExecutionRepo executionRepo;
    private final ColumnMappingService columnMappingService;
    private final ObjectMapper objectMapper;

    @Transactional
    public EtlExecution start(VwEtlTableWithSource table, String job,
        LocalDate bizDate, Long queueId, int attemptNo)
    {
        return start(table, job, bizDate, queueId, attemptNo, null);
    }

    @Transactional
    public EtlExecution start(VwEtlTableWithSource table, String job,
        LocalDate bizDate, Long queueId, int attemptNo, TargetContractReport contractReport)
    {
        List<ColumnMappingService.ResolvedColumnMapping> mappings =
            columnMappingService.resolve(table.getId());
        EtlExecution execution = new EtlExecution();
        execution.setTid(table.getId());
        execution.setJobQueueId(queueId);
        execution.setAttemptNo(Math.max(attemptNo, 1));
        execution.setBizDate(bizDate);
        execution.setSourceDb(table.getSourceDb());
        execution.setSourceTable(table.getSourceTable());
        execution.setSourceName(table.getName());
        execution.setSourceId(table.getSid());
        execution.setTargetDb(table.getTargetDb());
        execution.setTargetTable(table.getTargetTable());
        execution.setTargetCode(table.getTargetCode());
        execution.setTargetName(table.getTargetName());
        execution.setStatus(RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution.setSnapshotVersion(SNAPSHOT_VERSION);
        execution.setJobDigest(job == null ? null : sha256(job));
        // The report arrives on a separate HTTP request with no authenticated identity. A per-attempt
        // token carried in addax's jobName is the only thing that proves the caller is the process
        // this execution launched.
        execution.setReportToken(UUID.randomUUID().toString().replace("-", ""));
        execution.setConfigSnapshot(buildSnapshot(table, mappings, job, bizDate, contractReport));
        return executionRepo.save(execution);
    }

    @Transactional
    public void finish(EtlExecution execution, boolean success, String error)
    {
        if (execution == null || execution.getId() == null) {
            return;
        }
        execution.setStatus(success ? SUCCEEDED : FAILED);
        execution.setFinishedAt(LocalDateTime.now());
        execution.setErrorMessage(error);
        executionRepo.save(execution);
    }

    /**
     * Close executions that can no longer be running: their queue row was requeued, reached a
     * terminal state, disappeared, or has an expired lease. Without this, a restart that prevents
     * the owner from writing its terminal state leaves the execution history showing a run that
     * never ends.
     *
     * @return number of executions closed
     */
    @Transactional
    public int failOrphanedRunningExecutions()
    {
        int closed = executionRepo.failOrphanedRunningExecutions(
            "Interrupted: queue ownership lost (service restart or lease expiry)");
        if (closed > 0) {
            log.warn("Marked {} orphaned running execution(s) as failed", closed);
        }
        return closed;
    }

    public Page<EtlExecution> list(long tid, int page, int pageSize)
    {
        return executionRepo.findByTidOrderByStartedAtDescIdDesc(
            tid, PageRequest.of(Math.max(page, 0), Math.max(pageSize, 1)));
    }

    /**
     * Resolve the execution a job report names, but only when it belongs to the reported table.
     * Callers still have to verify the report token; the join alone does not prove authorship.
     */
    public Optional<EtlExecution> findReportTarget(long executionId, long tid)
    {
        return executionRepo.findById(executionId)
            .filter(execution -> execution.getTid() != null && execution.getTid() == tid);
    }

    /**
     * Claim the single report this attempt may submit. Callers must run inside the transaction that
     * persists the report: a rollback releases the claim, so a failed report can be retried, while a
     * committed claim blocks replays for good.
     */
    @Transactional
    public boolean claimReport(long executionId)
    {
        return executionRepo.claimReport(executionId) > 0;
    }

    private JsonNode buildSnapshot(VwEtlTableWithSource table,
        List<ColumnMappingService.ResolvedColumnMapping> mappings, String job,
        LocalDate bizDate, TargetContractReport contractReport)
    {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotVersion", SNAPSHOT_VERSION);

        ObjectNode tableNode = root.putObject("table");
        tableNode.put("tid", table.getId());
        tableNode.put("sourceDb", table.getSourceDb());
        tableNode.put("sourceTable", table.getSourceTable());
        tableNode.put("sourceName", table.getName());
        tableNode.put("sourceId", table.getSid());
        tableNode.put("targetDb", table.getTargetDb());
        tableNode.put("targetTable", table.getTargetTable());
        tableNode.put("targetCode", table.getTargetCode());
        tableNode.put("targetName", table.getTargetName());
        tableNode.put("filter", table.getFilter());
        tableNode.put("writeMode", table.getWriteMode());
        tableNode.put("calendar", table.getCalendar());
        tableNode.put("startAt", table.getStartAt());
        tableNode.put("schemaManagementMode",
            table.getSchemaManagementMode() == null ? "MANAGED" : table.getSchemaManagementMode().name());
        tableNode.put("targetSchemaFingerprint", table.getTargetSchemaFingerprint());
        if (table.getTargetOptions() != null) {
            tableNode.set("targetOptions", table.getTargetOptions());
        }

        ArrayNode columnNodes = root.putArray("columns");
        for (ColumnMappingService.ResolvedColumnMapping mapping : mappings) {
            EtlColumn column = mapping.sourceColumn();
            if (column == null) {
                continue;
            }
            ObjectNode node = columnNodes.addObject();
            node.put("name", column.getColumnName());
            node.put("sourceName", mapping.sourceColumnName());
            node.put("targetName", mapping.targetColumnName());
            node.put("ordinal", mapping.ordinalNo());
            node.put("sourceActive", mapping.sourceActive());
            node.put("sourceType", column.getSourceType());
            node.put("targetType", column.getTargetType());
            node.put("targetTypeFull", column.getTargetTypeFull());
            node.put("dataLength", column.getDataLength());
            node.put("dataPrecision", column.getDataPrecision());
            node.put("dataScale", column.getDataScale());
            node.put("comment", column.getColComment());
        }
        if (contractReport != null)
        {
            root.set("schemaValidation", objectMapper.valueToTree(contractReport));
        }

        ObjectNode executionNode = root.putObject("execution");
        executionNode.put("bizDate", bizDate.toString());
        executionNode.put("jobPresent", job != null && !job.isBlank());
        if (job != null) {
            executionNode.put("jobDigest", sha256(job));
        }
        return root;
    }

    private String sha256(String value)
    {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
