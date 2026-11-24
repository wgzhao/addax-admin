package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.alert.AlertDispatcher;
import com.wgzhao.addax.admin.alert.AlertSeverity;
import com.wgzhao.addax.admin.alert.model.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility facade for the collection and audit code paths.
 *
 * <p>The old implementation held the alert state in Redis and posted straight to
 * a WeCom webhook configured by environment variables. That path is gone: the
 * event store is now the single source of truth, routing is rule-based, and the
 * asynchronous outbox owns delivery. Call sites keep their signatures.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService
{
    private final AlertDispatcher alertDispatcher;

    /**
     * Reports a collection failure. Only the final attempt of a retry chain is
     * actionable; intermediate failures are noise.
     */
    public void reportCollectionFailure(long tid, String sourceDb, String sourceTable, String error,
        int attempt, int maxAttempts, boolean finalAttempt)
    {
        if (!finalAttempt) {
            log.debug("采集任务 {} 第 {}/{} 次尝试失败,非最终失败,暂不告警: {}", tid, attempt, maxAttempts, error);
            return;
        }
        try {
            alertDispatcher.publishCollectionFailure(tid, sourceDb, sourceTable, error, attempt, maxAttempts);
        }
        catch (Exception ex) {
            // Alerting must never turn a collection failure into a scheduler failure.
            log.error("发布采集失败告警事件失败 tid={}", tid, ex);
        }
    }

    /**
     * Reports a successful collection. A recovery event is only produced when an
     * open failure exists for this table; the dispatcher owns that check.
     */
    public void reportCollectionSuccess(long tid, String sourceDb, String sourceTable)
    {
        try {
            alertDispatcher.publishCollectionRecovery(tid, sourceDb, sourceTable);
        }
        catch (Exception ex) {
            log.error("发布采集恢复告警事件失败 tid={}", tid, ex);
        }
    }

    /**
     * Generic operational alert (calendar exhausted, scheduler scan failure,
     * schema fetch failure). Kept for call-site compatibility.
     */
    public void sendToWeComRobot(String message)
    {
        try {
            alertDispatcher.publishSystem(message);
        }
        catch (Exception ex) {
            log.error("发布系统告警事件失败", ex);
        }
    }

    public void sendSecurityAlert(String message)
    {
        try {
            alertDispatcher.publishSecurity(message);
        }
        catch (Exception ex) {
            log.error("发布安全告警事件失败", ex);
        }
    }

    /**
     * Scheduler-domain failure (day-cut refresh, scheduler liveness). The key suffix
     * identifies the condition, not the message text: a retry must increment the
     * existing open event instead of creating another one.
     */
    public void sendScheduleAlert(String keySuffix, String title, String message,
        AlertSeverity severity, Map<String, String> data)
    {
        try {
            alertDispatcher.publishSchedule(keySuffix, title, message, severity, data);
        }
        catch (Exception ex) {
            log.error("发布调度告警事件失败 key={}", keySuffix, ex);
        }
    }

    /**
     * Informational daily summary. Delivered only when a DAILY rule exists; the
     * dispatcher owns that rule check.
     */
    public void sendDailySummary(String keySuffix, String title, String message, Map<String, String> data)
    {
        try {
            alertDispatcher.publishDaily(keySuffix, title, message, data);
        }
        catch (Exception ex) {
            log.error("发布每日汇总事件失败 key={}", keySuffix, ex);
        }
    }

    /** Resolves superseded day-cut events; returns the events that were closed. */
    public List<AlertEvent> resolveScheduleEvents(String prefix, Set<String> keepKeys)
    {
        try {
            return alertDispatcher.resolveOpenScheduleEvents(prefix, keepKeys);
        }
        catch (Exception ex) {
            log.error("事件恢复失败 prefix={}", prefix, ex);
            return List.of();
        }
    }

    /**
     * Tells the channels that received a schedule failure that the condition is
     * gone. Delivery goes only to that original audience, so a recovery can never
     * introduce a new channel.
     */
    public void sendScheduleRecovery(String keySuffix, String title, String message,
        Map<String, String> data, List<AlertEvent> resolvedEvents)
    {
        try {
            alertDispatcher.publishScheduleRecovery(keySuffix, title, message, data, resolvedEvents);
        }
        catch (Exception ex) {
            log.error("发布调度恢复事件失败 key={}", keySuffix, ex);
        }
    }
}
