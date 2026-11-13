package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.FailureCategory;
import com.wgzhao.addax.admin.model.EtlFailure;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlFailureRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The only writer of {@code etl_failure}.
 *
 * <p>Recording is best effort: it runs in its own transaction and swallows its own errors, because
 * losing diagnostic evidence must never turn a collection failure into a different outcome or roll
 * back the operation that failed.</p>
 */
@Service
@Slf4j
public class FailureRecorder
{
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int SUMMARY_MAX = 1000;
    private static final int DETAIL_MAX = 65_536;
    private static final String UNKNOWN_SUMMARY = "未知失败（没有捕获到错误输出）";

    private final EtlFailureRepo failureRepo;
    private final VwEtlTableWithSourceRepo tableViewRepo;
    private final TransactionTemplate requiresNew;

    public FailureRecorder(EtlFailureRepo failureRepo, VwEtlTableWithSourceRepo tableViewRepo,
        PlatformTransactionManager transactionManager)
    {
        this.failureRepo = failureRepo;
        this.tableViewRepo = tableViewRepo;
        this.requiresNew = new TransactionTemplate(transactionManager);
        // A failure must survive the rollback of the operation that produced it.
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Record one failed operation.
     *
     * @param tid       collection definition id; the fact is never dropped just because the definition
     *                  disappeared while the failure was being reported
     * @param summary   short, actionable message shown in lists and the status drawer
     * @param evidence  optional links and context; may be null
     */
    public void record(long tid, String summary, FailureEvidence evidence)
    {
        FailureEvidence ev = evidence == null ? FailureEvidence.create() : evidence;
        try {
            String normalized = normalizeSummary(summary);
            String stage = ev.stage();
            String errorClass = trimTo(ev.errorClass(), 200);
            String fingerprint = fingerprint(tid, stage, errorClass, normalized, ev.legacyJourId());

            requiresNew.executeWithoutResult(status -> {
                if (ev.legacyJourId() != null && failureRepo.existsByLegacyJourId(ev.legacyJourId())) {
                    return;
                }
                if (ev.executionId() != null
                    && failureRepo.existsByExecutionIdAndStageAndFingerprint(
                        ev.executionId(), stage, fingerprint))
                {
                    return;
                }
                EtlFailure failure = new EtlFailure();
                failure.setTid(tid);
                failure.setExecutionId(ev.executionId());
                failure.setJobQueueId(ev.jobQueueId());
                failure.setLogId(ev.logId());
                failure.setLogName(trimTo(ev.logName(), 255));
                failure.setOccurredAt(ev.occurredAt() == null ? LocalDateTime.now() : ev.occurredAt());
                failure.setStage(stage);
                failure.setCategory(FailureCategory.classify(normalized));
                failure.setErrorClass(errorClass);
                failure.setErrorCode(trimTo(ev.errorCode(), 64));
                failure.setSummary(normalized);
                failure.setDetail(normalizeDetail(ev.detail()));
                failure.setFingerprint(fingerprint);
                failure.setAttemptNo(ev.attemptNo());
                failure.setMaxAttempts(ev.maxAttempts());
                failure.setFinalAttempt(ev.finalAttempt() == null || ev.finalAttempt());
                failure.setRunType(ev.runType() == null || ev.runType().isBlank() ? "NORMAL" : ev.runType());
                failure.setBizDate(ev.bizDate());
                failure.setLegacyJourId(ev.legacyJourId());
                fillIdentity(failure, tid);
                failureRepo.save(failure);
            });
        }
        catch (DataIntegrityViolationException dup) {
            // The same attempt already recorded this failure; the unique index did its job.
            log.debug("Duplicate failure ignored tid={} stage={}", tid, ev.stage());
        }
        catch (Exception e) {
            log.error("Failed to record collection failure tid={} stage={}", tid, ev.stage(), e);
        }
    }

    private void fillIdentity(EtlFailure failure, long tid)
    {
        VwEtlTableWithSource table = tableViewRepo.findById(tid).orElse(null);
        if (table == null) {
            return;
        }
        failure.setSourceId(table.getSid());
        failure.setSourceDb(table.getSourceDb());
        failure.setSourceTable(table.getSourceTable());
        failure.setTargetDb(table.getTargetDb());
        failure.setTargetTable(table.getTargetTable());
    }

    public static String normalizeSummary(String raw)
    {
        if (raw == null) {
            return UNKNOWN_SUMMARY;
        }
        String text = ANSI_ESCAPE.matcher(raw.replace("\u0000", "")).replaceAll("");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (text.isEmpty()) {
            return UNKNOWN_SUMMARY;
        }
        return text.length() <= SUMMARY_MAX ? text : text.substring(0, SUMMARY_MAX - 1) + "…";
    }

    private static String normalizeDetail(String raw)
    {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = ANSI_ESCAPE.matcher(raw.replace("\u0000", "")).replaceAll("");
        return text.length() <= DETAIL_MAX ? text : text.substring(0, DETAIL_MAX);
    }

    private static String trimTo(String value, int max)
    {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Groups repeated occurrences of the same problem for one table. Deliberately conservative:
     * failing to group two similar errors is safer than merging two different ones.
     */
    private static String fingerprint(long tid, String stage, String errorClass, String summary,
        Long legacyJourId)
    {
        // Legacy rows are one fact per journal entry; folding their journal id into the fingerprint
        // keeps two identical errors from different attempts as separate facts instead of colliding
        // on the per-execution unique index and stalling the backfill.
        String material = tid + "|" + stage + "|" + (errorClass == null ? "" : errorClass)
            + "|" + summary.toLowerCase(Locale.ROOT)
            + (legacyJourId == null ? "" : "|legacy:" + legacyJourId);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
