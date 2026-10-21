package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlExecution;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import com.wgzhao.addax.admin.repository.EtlExecutionRepo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the immutable execution context. Historical facts point here instead of
 * resolving display data from mutable source, target, or table configuration.
 */
@Service
@RequiredArgsConstructor
public class EtlExecutionService
{
    public static final int SNAPSHOT_VERSION = 1;
    public static final String RUNNING = "running";
    public static final String SUCCEEDED = "succeeded";
    public static final String FAILED = "failed";

    private final EtlExecutionRepo executionRepo;
    private final EtlColumnRepo columnRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public EtlExecution start(VwEtlTableWithSource table, String job,
        LocalDate bizDate, Long queueId, int attemptNo)
    {
        List<EtlColumn> columns = columnRepo.findAllByTidOrderByColumnId(table.getId());
        EtlExecution execution = new EtlExecution();
        execution.setTid(table.getId());
        execution.setJobQueueId(queueId);
        execution.setAttemptNo(Math.max(attemptNo, 1));
        execution.setBizDate(bizDate);
        execution.setSourceDb(table.getSourceDb());
        execution.setSourceTable(table.getSourceTable());
        execution.setSourceCode(table.getCode());
        execution.setSourceName(table.getName());
        execution.setTargetDb(table.getTargetDb());
        execution.setTargetTable(table.getTargetTable());
        execution.setTargetCode(table.getTargetCode());
        execution.setTargetName(table.getTargetName());
        execution.setStatus(RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution.setSnapshotVersion(SNAPSHOT_VERSION);
        execution.setJobDigest(job == null ? null : sha256(job));
        execution.setConfigSnapshot(buildSnapshot(table, columns, job, bizDate));
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

    public Page<EtlExecution> list(long tid, int page, int pageSize)
    {
        return executionRepo.findByTidOrderByStartedAtDescIdDesc(
            tid, PageRequest.of(Math.max(page, 0), Math.max(pageSize, 1)));
    }

    private JsonNode buildSnapshot(VwEtlTableWithSource table, List<EtlColumn> columns, String job,
        LocalDate bizDate)
    {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotVersion", SNAPSHOT_VERSION);

        ObjectNode tableNode = root.putObject("table");
        tableNode.put("tid", table.getId());
        tableNode.put("sourceDb", table.getSourceDb());
        tableNode.put("sourceTable", table.getSourceTable());
        tableNode.put("sourceCode", table.getCode());
        tableNode.put("sourceName", table.getName());
        tableNode.put("targetDb", table.getTargetDb());
        tableNode.put("targetTable", table.getTargetTable());
        tableNode.put("targetCode", table.getTargetCode());
        tableNode.put("targetName", table.getTargetName());
        tableNode.put("filter", table.getFilter());
        tableNode.put("kind", table.getKind());
        tableNode.put("writeMode", table.getWriteMode());
        tableNode.put("calendar", table.getCalendar());
        tableNode.put("startAt", table.getStartAt());
        if (table.getTargetOptions() != null) {
            tableNode.set("targetOptions", table.getTargetOptions());
        }

        ArrayNode columnNodes = root.putArray("columns");
        for (EtlColumn column : columns) {
            ObjectNode node = columnNodes.addObject();
            node.put("name", column.getColumnName());
            node.put("sourceType", column.getSourceType());
            node.put("targetType", column.getTargetType());
            node.put("targetTypeFull", column.getTargetTypeFull());
            node.put("dataLength", column.getDataLength());
            node.put("dataPrecision", column.getDataPrecision());
            node.put("dataScale", column.getDataScale());
            node.put("comment", column.getColComment());
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
