package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.alert.AlertDispatcher;
import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.redis.WorkerHeartbeatService;
import com.wgzhao.addax.admin.redis.WorkerHeartbeatService.WorkerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodic check that the cluster's running counters and the persisted queue agree.
 *
 * <p>The capacity counters live in memory and the queue lives in the database, so a lost increment,
 * a crashed worker or a broken lease sweep leaves them disagreeing with no error anywhere. The first
 * capacity bug (a negative running count) only surfaced by accident; this check turns the same
 * condition into an alert. It intentionally runs on the master only: every node has the same queue
 * rows, and duplicate checks would duplicate events.</p>
 *
 * <p>A one-off mismatch is normal: heartbeats are published every 15 seconds, so a job that started
 * or finished in between can shift the two numbers. Only a mismatch that survives two consecutive
 * checks is reported. Negative counters and leases expired well past the sweep window are reported
 * immediately because neither can be explained by the heartbeat lag.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StateConsistencyChecker
{
    static final String EVENT_KEY = "STATE:CONSISTENCY";

    private static final int MISMATCH_STREAK_THRESHOLD = 2;

    /** The lease sweep runs far more often than this; an older lease means the sweep is stuck. */
    private static final String LEAKED_LEASE_SQL = """
        select count(*) from public.etl_job_queue
         where status = 'running' and lease_until < now() - interval '2 minutes'
        """;

    private static final String RUNNING_QUEUE_SQL =
        "select count(*) from public.etl_job_queue where status = 'running'";

    private final WorkerHeartbeatService heartbeatService;
    private final MasterElectionService electionService;
    private final AlertDispatcher alertDispatcher;
    private final JdbcTemplate jdbcTemplate;

    private int mismatchStreak;

    @Scheduled(fixedDelayString = "${STATE_CONSISTENCY_CHECK_MS:300000}",
        initialDelayString = "${STATE_CONSISTENCY_INITIAL_DELAY_MS:60000}")
    public void check()
    {
        if (!electionService.isMaster()) {
            return;
        }
        try {
            runCheck();
        }
        catch (Exception e) {
            // A failed check must not kill the scheduler for every later tick.
            log.warn("State consistency check failed: {}", e.getMessage(), e);
        }
    }

    void runCheck()
    {
        List<WorkerInfo> workers = heartbeatService.getAliveWorkers();
        List<String> findings = new ArrayList<>();
        long heartbeatRunning = 0;
        for (WorkerInfo worker : workers) {
            heartbeatRunning += worker.running();
            if (worker.running() < 0) {
                findings.add("节点 " + worker.host() + " 全局 running 计数为 " + worker.running());
            }
            Map<Integer, Integer> negative = worker.sourceRunningNegative();
            if (negative != null) {
                negative.forEach((sid, count) ->
                    findings.add("节点 " + worker.host() + " 源 " + sid + " running 计数为 " + count));
            }
        }

        int queueRunning = count(RUNNING_QUEUE_SQL);
        int leakedLeases = count(LEAKED_LEASE_SQL);
        if (leakedLeases > 0) {
            findings.add("队列中 " + leakedLeases + " 条 running 行的租约已过期超过 2 分钟");
        }

        boolean mismatch = queueRunning != heartbeatRunning;
        if (mismatch) {
            mismatchStreak++;
            if (mismatchStreak >= MISMATCH_STREAK_THRESHOLD) {
                findings.add("队列 running 行数 " + queueRunning + " 与心跳 running 合计 "
                    + heartbeatRunning + " 不一致（连续 " + mismatchStreak + " 次检查）");
            }
            else {
                log.info("State consistency mismatch observed once (queue={}, heartbeat={}); waiting for the next check",
                    queueRunning, heartbeatRunning);
            }
        }
        else {
            mismatchStreak = 0;
        }

        if (!findings.isEmpty()) {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("queueRunning", String.valueOf(queueRunning));
            data.put("heartbeatRunning", String.valueOf(heartbeatRunning));
            data.put("leakedLeases", String.valueOf(leakedLeases));
            data.put("nodes", String.valueOf(workers.size()));
            String message = String.join("；", findings);
            log.warn("State consistency alert: {}", message);
            alertDispatcher.publishSystemCondition(EVENT_KEY, "集群状态一致性告警", message, data);
            return;
        }
        if (mismatch) {
            // Do not resolve an open event while a first mismatch is still unconfirmed.
            return;
        }
        int resolved = alertDispatcher.resolveOpenSystemEvents(EVENT_KEY);
        if (resolved > 0) {
            log.info("State consistency recovered; resolved {} open event(s)", resolved);
        }
    }

    private int count(String sql)
    {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
