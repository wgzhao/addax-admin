package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.alert.AlertSeverity;
import com.wgzhao.addax.admin.alert.model.AlertEvent;
import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.DayCutOutcome;
import com.wgzhao.addax.admin.dto.TableRefreshStats;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns a day-cut outcome into the facts the subsystem now keeps apart: an audit
 * record for every completed run, a SCHEDULE failure event that stays open until
 * a later run succeeds, a P2 notice for tables a successful run could not
 * refresh, and a recovery notice to the channels that saw the failure. A clean
 * run with nothing open is silent. Alerting never propagates into the refresh
 * itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DayCutAlerter
{
    private static final String DAYCUT_KEY_PREFIX = "SCHEDULE:DAYCUT";
    private static final int MAX_LISTED_FAILURES = 10;

    private final AlertService alertService;
    private final AuditRecorder auditRecorder;
    private final EtlTableRepo etlTableRepo;

    public void report(DayCutOutcome outcome)
    {
        if (outcome.skipped()) {
            log.info("Day-cut run on {} skipped: {}", outcome.bizDate(), outcome.skipReason());
            return;
        }
        String date = outcome.bizDate().toString();
        TableRefreshStats refresh = outcome.refresh();
        int remainingWaitSchema = etlTableRepo.countByStatusEquals("U");
        Map<String, String> data = new LinkedHashMap<>();
        data.put("stage", outcome.stage().name());
        data.put("durationSecs", String.valueOf(outcome.durationMs() / 1000));
        data.put("timedOut", String.valueOf(outcome.timedOut()));
        data.put("queueMonitorRestarted", String.valueOf(outcome.queueMonitorRestarted()));
        data.put("doneMarkerWritten", String.valueOf(outcome.doneMarkerWritten()));
        data.put("resetTables", String.valueOf(outcome.resetTables()));
        data.put("refreshTotal", String.valueOf(refresh.total()));
        data.put("refreshSuccess", String.valueOf(refresh.success()));
        data.put("refreshFailed", String.valueOf(refresh.failed()));
        data.put("sourceUnavailable", String.valueOf(refresh.sourceUnavailable()));
        data.put("waitSchemaToNotCollect", String.valueOf(refresh.waitSchemaToNotCollect()));
        data.put("remainingWaitSchema", String.valueOf(remainingWaitSchema));

        auditRecorder.success("DAILY_REFRESH_COMPLETED", "SCHEDULING",
            outcome.success() ? "INFO" : "WARNING", "SYSTEM", "refresh", "daily refresh",
            null, Map.copyOf(data));

        if (outcome.success()) {
            boolean tableProblems = refresh.failed() > 0 || remainingWaitSchema > 0;
            if (!tableProblems) {
                List<AlertEvent> resolved = alertService.resolveScheduleEvents(DAYCUT_KEY_PREFIX, Set.of());
                // A clean day cut is silent, but a run that clears an earlier failure
                // must tell the channels that saw that failure, otherwise recovery is
                // only visible to whoever happens to watch the events page.
                if (!resolved.isEmpty()) {
                    alertService.sendScheduleRecovery("DAYCUT_RECOVERY:" + date,
                        "切日刷新已恢复 · " + date, recoveryMessage(outcome), data, resolved);
                }
                return;
            }
            String tableKey = "SCHEDULE:DAYCUT_TABLES:" + date;
            alertService.resolveScheduleEvents(DAYCUT_KEY_PREFIX, Set.of(tableKey));
            alertService.sendScheduleAlert("DAYCUT_TABLES:" + date,
                "切日表结构刷新有失败 · " + date,
                tableFailureMessage(refresh, remainingWaitSchema), AlertSeverity.P2, data);
            return;
        }

        String failureKey = "SCHEDULE:DAYCUT:" + date;
        alertService.resolveScheduleEvents(DAYCUT_KEY_PREFIX,
            Set.of(failureKey, "SCHEDULE:DAYCUT_TABLES:" + date));
        alertService.sendScheduleAlert("DAYCUT:" + date, "切日刷新失败 · " + date,
            hardFailureMessage(outcome), AlertSeverity.P1, data);
    }

    private String hardFailureMessage(DayCutOutcome outcome)
    {
        StringBuilder text = new StringBuilder();
        text.append("切日刷新失败，阶段：").append(stageLabel(outcome.stage()))
            .append("，耗时 ").append(outcome.durationMs() / 1000).append(" 秒");
        if (outcome.timedOut()) {
            text.append("（超过 ").append(outcome.timeoutSeconds()).append(" 秒超时阈值）");
        }
        text.append("。\n");
        if (outcome.error() != null && !outcome.error().isBlank()) {
            text.append("原因：").append(outcome.error()).append("\n");
        }
        text.append("表状态重置：").append(outcome.resetTables()).append(" 张\n");
        TableRefreshStats refresh = outcome.refresh();
        text.append("表结构刷新：成功 ").append(refresh.success())
            .append("，失败 ").append(refresh.failed()).append("（计划 ").append(refresh.total()).append(" 张）\n");
        text.append("队列监控已恢复：").append(outcome.queueMonitorRestarted() ? "是" : "否").append("\n");
        text.append(outcome.doneMarkerWritten()
            ? "完成标记已写入：是\n"
            : "完成标记未写入：master 变更或下次触发时会自动补跑，期间任务模板仍是旧的。\n");
        text.append("影响：未完成刷新的表不会自动建/改目标表，相关采集可能失败或继续写入旧结构，需人工处理。");
        appendFailureList(text, refresh);
        return text.toString();
    }

    private String recoveryMessage(DayCutOutcome outcome)
    {
        TableRefreshStats refresh = outcome.refresh();
        return "重试后切日刷新已成功完成。\n"
            + "表状态重置：" + outcome.resetTables() + " 张\n"
            + "表结构刷新：成功 " + refresh.success() + "，失败 " + refresh.failed()
            + "（计划 " + refresh.total() + " 张）\n"
            + "耗时：" + (outcome.durationMs() / 1000) + " 秒";
    }

    private String tableFailureMessage(TableRefreshStats refresh, int remainingWaitSchema)
    {
        StringBuilder text = new StringBuilder();
        text.append("切日刷新已按计划完成，但有 ").append(refresh.failed())
            .append(" 张表刷新失败");
        if (remainingWaitSchema > 0) {
            text.append("，").append(remainingWaitSchema).append(" 张表仍为 U（等待更新）");
        }
        text.append("。这些表本次不会参与自动采集，需人工处理后再执行「更新表结构」。\n");
        text.append("本轮共刷新 ").append(refresh.total()).append(" 张表，成功 ")
            .append(refresh.success()).append(" 张，其中源不可达 ")
            .append(refresh.sourceUnavailable()).append(" 张，U 恢复为 N ")
            .append(refresh.waitSchemaToNotCollect()).append(" 张。");
        appendFailureList(text, refresh);
        return text.toString();
    }

    private void appendFailureList(StringBuilder text, TableRefreshStats refresh)
    {
        List<TableRefreshStats.Failure> failures = refresh.failures();
        if (failures.isEmpty()) {
            return;
        }
        text.append("\n失败表：\n");
        String listed = failures.stream()
            .limit(MAX_LISTED_FAILURES)
            .map(failure -> "· " + tableDesc(failure) + "：" + failure.reason())
            .collect(Collectors.joining("\n"));
        text.append(listed);
        if (failures.size() > MAX_LISTED_FAILURES) {
            text.append("\n……其余 ").append(failures.size() - MAX_LISTED_FAILURES).append(" 张见事件详情。");
        }
    }

    private String tableDesc(TableRefreshStats.Failure failure)
    {
        String db = failure.sourceDb() == null ? "" : failure.sourceDb();
        String table = failure.sourceTable() == null ? "" : failure.sourceTable();
        String name = db.isEmpty() ? table : (table.isEmpty() ? db : db + "." + table);
        return (name.isEmpty() ? "任务" : name) + "(" + failure.tid() + ")";
    }

    private String stageLabel(DayCutOutcome.Stage stage)
    {
        return switch (stage) {
            case RESET_FLAGS -> "重置采集状态";
            case LOAD_CONFIG -> "重载系统配置";
            case REFRESH_SCHEMA -> "刷新表结构";
            case TRUNCATE_QUEUE -> "清理采集队列";
            case NONE -> "准备";
        };
    }
}
