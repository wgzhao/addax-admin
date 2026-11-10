package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.alert.AlertDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}
